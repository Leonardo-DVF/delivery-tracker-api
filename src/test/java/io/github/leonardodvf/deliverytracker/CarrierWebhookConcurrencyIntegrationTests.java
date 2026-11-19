package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt.Outcome;
import io.github.leonardodvf.deliverytracker.webhook.service.CarrierWebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.quote;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.TEST_SECRET;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.signature;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The carrier retries aggressively: the same signed event arrives several times at once. Runs without a
 * test transaction so each delivery commits on its own connection.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CarrierWebhookConcurrencyIntegrationTests extends JwtTestSupport {

    @Autowired private CarrierWebhookService webhooks;
    @Autowired private ShipmentService shipments;
    @Autowired private UserRepository users;
    @Autowired private FreightQuoteRepository quotes;
    @Autowired private JdbcTemplate jdbc;
    private UUID customerId;

    @Test
    void concurrentRedeliveriesOfOneEventApplyItExactlyOnce() throws Exception {
        var customer = users.saveAndFlush(new User("Owner", "webhook-race@example.test", "hash", UserRole.CUSTOMER));
        customerId = customer.getId();
        var quote = quotes.saveAndFlush(quote(customer, "01000000", Instant.now()));
        var shipment = shipments.create(customerId, request(quote.getId()));
        var body = ("{\"eventId\":\"evt-race-1\",\"trackingCode\":\"" + shipment.trackingCode()
                + "\",\"status\":\"PICKED_UP\",\"occurredAt\":\"" + Instant.now() + "\"}").getBytes(StandardCharsets.UTF_8);
        var timestamp = timestamp(Instant.now());
        var signature = signature(TEST_SECRET, timestamp, body);

        int deliveries = 8;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(deliveries);
        List<Future<WebhookReceipt>> results = new ArrayList<>();
        var outcomes = new EnumMap<Outcome, Integer>(Outcome.class);
        try {
            for (int i = 0; i < deliveries; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return webhooks.receive(timestamp, signature, body);
                }));
            }
            start.countDown();
            for (var result : results) {
                try {
                    outcomes.merge(result.get().outcome(), 1, Integer::sum);
                } catch (ExecutionException failure) {
                    // Lost the @Version race; the carrier retries and then gets DUPLICATE.
                    assertThat(failure.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes.get(Outcome.APPLIED)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tracking_events WHERE carrier_event_id = 'evt-race-1'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipment.id()))
                .isEqualTo("PICKED_UP");
        // A late retry after the dust settles is recognized by its id.
        assertThat(webhooks.receive(timestamp, signature, body).outcome()).isEqualTo(Outcome.DUPLICATE);
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
