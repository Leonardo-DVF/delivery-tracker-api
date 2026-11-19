package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.signature;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.timestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The carrier's side over real HTTP, real HMAC and PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class CarrierWebhookIntegrationTests extends JwtTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ShipmentService shipmentService;
    @Autowired private FreightService freight;
    @Autowired private ShipmentRepository shipments;
    @Autowired private TrackingEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    private ShipmentResponse shipment;

    @BeforeEach
    void createShipment() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        shipment = shipmentService.create(customer.getId(), request(quote.getId()));
        entityManager.flush();
    }

    @Test
    void appliesASignedEventAndShowsItInPublicTracking() throws Exception {
        var occurredAt = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);

        deliver(body("evt-1", "PICKED_UP", occurredAt, "Rio hub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));
        flushAndClear();

        assertThat(shipments.findById(shipment.id()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        var carrierEvent = events.findByShipmentIdOrderByReceivedAtAscOccurredAtAsc(shipment.id()).getLast();
        assertThat(carrierEvent.getSource()).isEqualTo("CARRIER");
        assertThat(carrierEvent.getCarrierEventId()).isEqualTo("evt-1");
        assertThat(carrierEvent.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(carrierEvent.getReceivedAt()).isAfter(occurredAt);
        mvc.perform(get("/api/v1/tracking/" + shipment.trackingCode()))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.events[1].location").value("Rio hub"));
    }

    @Test
    void redeliveryOfTheSameEventIsReportedAsDuplicateAndStoredOnce() throws Exception {
        var body = body("evt-1", "PICKED_UP", Instant.now(), null);
        deliver(body).andExpect(jsonPath("$.outcome").value("APPLIED"));
        flushAndClear();

        deliver(body).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        flushAndClear();

        assertThat(events.findByShipmentIdOrderByReceivedAtAscOccurredAtAsc(shipment.id()))
                .filteredOn(event -> "CARRIER".equals(event.getSource())).hasSize(1);
    }

    @Test
    void outOfOrderEventIsRetriedUntilItsPredecessorArrives() throws Exception {
        var base = Instant.now().minus(Duration.ofHours(1));
        deliver(body("evt-1", "PICKED_UP", base, null)).andExpect(jsonPath("$.outcome").value("APPLIED"));
        flushAndClear();
        var outForDelivery = body("evt-3", "OUT_FOR_DELIVERY", base.plusSeconds(120), null);

        deliver(outForDelivery).andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PICKED_UP"));
        deliver(body("evt-2", "IN_TRANSIT", base.plusSeconds(60), null)).andExpect(jsonPath("$.outcome").value("APPLIED"));
        flushAndClear();
        deliver(outForDelivery).andExpect(jsonPath("$.outcome").value("APPLIED"));
        flushAndClear();
        deliver(body("evt-4", "IN_TRANSIT", base.plusSeconds(90), null)).andExpect(jsonPath("$.outcome").value("IGNORED"));
        flushAndClear();

        assertThat(shipments.findById(shipment.id()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(events.findByShipmentIdOrderByReceivedAtAscOccurredAtAsc(shipment.id()))
                .extracting(TrackingEvent::getStatus).containsExactly(ShipmentStatus.CREATED, ShipmentStatus.PICKED_UP,
                        ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(events.existsByCarrierEventId("evt-4")).isFalse();
    }

    @Test
    void forgedOrStaleRequestsChangeNothing() throws Exception {
        var body = body("evt-1", "PICKED_UP", Instant.now(), null);
        var now = timestamp(Instant.now());
        var stale = timestamp(Instant.now().minus(Duration.ofMinutes(10)));

        send(body, now, signature("forged-secret-forged-secret-forged-secret", now, body.getBytes()))
                .andExpect(status().isUnauthorized());
        send(body, stale, signature(stale, body)).andExpect(status().isUnauthorized());
        send(body.replace("PICKED_UP", "CANCELED"), now, signature(now, body)).andExpect(status().isUnauthorized());
        send(body, null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or missing webhook signature"));
        flushAndClear();

        assertThat(shipments.findById(shipment.id()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(events.existsByCarrierEventId("evt-1")).isFalse();
    }

    @Test
    void unknownTrackingCodeIsNotFound() throws Exception {
        deliver(body("evt-1", "PICKED_UP", Instant.now(), null).replace(shipment.trackingCode(), "A".repeat(32)))
                .andExpect(status().isNotFound());
    }

    private String body(String eventId, String status, Instant occurredAt, String location) {
        return "{\"eventId\":\"" + eventId + "\",\"trackingCode\":\"" + shipment.trackingCode() + "\",\"status\":\"" + status
                + "\",\"occurredAt\":\"" + occurredAt + "\"" + (location == null ? "" : ",\"location\":\"" + location + "\"") + "}";
    }

    private ResultActions deliver(String body) throws Exception {
        var now = timestamp(Instant.now());
        return send(body, now, signature(now, body));
    }

    private ResultActions send(String body, String timestamp, String signature) throws Exception {
        var request = post("/api/v1/webhooks/carrier").contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (timestamp != null) {
            request.header("X-Carrier-Timestamp", timestamp).header("X-Carrier-Signature", signature);
        }
        return mvc.perform(request);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
