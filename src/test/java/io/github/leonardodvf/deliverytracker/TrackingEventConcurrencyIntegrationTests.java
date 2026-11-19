package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.service.TrackingService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.quote;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs without a test transaction so each request commits on its own connection,
 * exercising the @Version check on shipments under real concurrency.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TrackingEventConcurrencyIntegrationTests extends JwtTestSupport {

    @Autowired private TrackingService tracking;
    @Autowired private ShipmentService shipmentService;
    @Autowired private UserRepository users;
    @Autowired private FreightQuoteRepository quotes;
    @Autowired private JdbcTemplate jdbc;
    private UUID customerId;
    private UUID operatorId;

    @Test
    void onlyOneOfManyConcurrentStatusChangesWins() throws Exception {
        var customer = users.saveAndFlush(new User("Owner", "tracking-race@example.test", "hash", UserRole.CUSTOMER));
        var operator = users.saveAndFlush(new User("Operator", "tracking-race-op@example.test", "hash", UserRole.OPERATOR));
        customerId = customer.getId();
        operatorId = operator.getId();
        var quote = quotes.saveAndFlush(quote(customer, "01000000", Instant.now()));
        var shipmentId = shipmentService.create(customerId, request(quote.getId())).id();
        int attempts = 8;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(attempts);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit((Callable<Object>) () -> {
                    start.await();
                    return tracking.record(operatorId, shipmentId, new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, null));
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (var result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException failure) {
                    // Losers either saw a stale version or read the already-updated status.
                    assertThat(failure.getCause()).isInstanceOfAny(
                            OptimisticLockingFailureException.class, InvalidStatusTransitionException.class);
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipmentId))
                .isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tracking_events WHERE shipment_id = ? AND status = 'PICKED_UP'",
                Long.class, shipmentId)).isEqualTo(1);
    }

    @AfterEach
    void removeCommittedRows() {
        if (customerId == null) {
            return;
        }
        jdbc.update("DELETE FROM tracking_events WHERE shipment_id IN (SELECT id FROM shipments WHERE customer_id = ?)", customerId);
        jdbc.update("DELETE FROM shipments WHERE customer_id = ?", customerId);
        jdbc.update("DELETE FROM freight_quotes WHERE requester_id = ?", customerId);
        jdbc.update("DELETE FROM app_users WHERE id IN (?, ?)", customerId, operatorId);
    }
}
