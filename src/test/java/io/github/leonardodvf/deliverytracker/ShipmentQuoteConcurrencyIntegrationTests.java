package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteAlreadyUsedException;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
 * exercising the unique constraint on shipments.quote_id under real concurrency.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShipmentQuoteConcurrencyIntegrationTests extends JwtTestSupport {

    @Autowired private ShipmentService service;
    @Autowired private UserRepository users;
    @Autowired private FreightQuoteRepository quotes;
    @Autowired private JdbcTemplate jdbc;
    private UUID customerId;

    @Test
    void onlyOneOfManyConcurrentRequestsCanUseTheSameQuote() throws Exception {
        var customer = users.saveAndFlush(new User("Racer", "racer@example.test", "hash", UserRole.CUSTOMER));
        customerId = customer.getId();
        var quote = quotes.saveAndFlush(quote(customer, "01000000", Instant.now()));
        int attempts = 8;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(attempts);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit((Callable<Object>) () -> {
                    start.await();
                    return service.create(customerId, request(quote.getId()));
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (var result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOfAny(
                            FreightQuoteAlreadyUsedException.class, DataIntegrityViolationException.class);
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipments WHERE quote_id = ?", Long.class, quote.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tracking_events e JOIN shipments s ON s.id = e.shipment_id WHERE s.quote_id = ?
                """, Long.class, quote.getId())).isEqualTo(1);
    }

    @AfterEach
    void removeCommittedRows() {
        if (customerId == null) {
            return;
        }
        jdbc.update("DELETE FROM tracking_events WHERE shipment_id IN (SELECT id FROM shipments WHERE customer_id = ?)", customerId);
        jdbc.update("DELETE FROM shipments WHERE customer_id = ?", customerId);
        jdbc.update("DELETE FROM freight_quotes WHERE requester_id = ?", customerId);
        jdbc.update("DELETE FROM app_users WHERE id = ?", customerId);
    }
}
