package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.tracking.service.TrackingService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class TrackingEventIntegrationTests extends JwtTestSupport {

    @Autowired private TrackingService tracking;
    @Autowired private ShipmentService shipmentService;
    @Autowired private FreightService freight;
    @Autowired private ShipmentRepository shipments;
    @Autowired private TrackingEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    private User operator;
    private UUID shipmentId;

    @BeforeEach
    void createShipment() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        operator = users.saveAndFlush(new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        shipmentId = shipmentService.create(customer.getId(), request(quote.getId())).id();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void walksTheWholeLifecycleAndKeepsTheHistoryInOrder() {
        record(ShipmentStatus.PICKED_UP, "Rio de Janeiro hub");
        record(ShipmentStatus.IN_TRANSIT, null);
        record(ShipmentStatus.OUT_FOR_DELIVERY, "São Paulo");
        record(ShipmentStatus.DELIVERED, "São Paulo");

        var stored = shipments.findById(shipmentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(stored.getVersion()).isEqualTo(4);
        var history = events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipmentId);
        assertThat(history).extracting(TrackingEvent::getStatus).containsExactly(ShipmentStatus.CREATED,
                ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED);
        assertThat(history).extracting(TrackingEvent::getSource).containsExactly("API", "OPERATOR", "OPERATOR", "OPERATOR", "OPERATOR");
        assertThat(history.get(1).getLocation()).isEqualTo("Rio de Janeiro hub");
        assertThat(history.getLast().getOccurredAt()).isEqualTo(stored.getUpdatedAt());
    }

    @Test
    void rejectsSkippingAStepAndLeavesTheDatabaseUntouched() {
        assertThatThrownBy(() -> record(ShipmentStatus.OUT_FOR_DELIVERY, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        entityManager.clear();

        assertThat(shipments.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipmentId)).hasSize(1);
    }

    @Test
    void rejectsChangesAfterDelivery() {
        record(ShipmentStatus.PICKED_UP, null);
        record(ShipmentStatus.IN_TRANSIT, null);
        record(ShipmentStatus.OUT_FOR_DELIVERY, null);
        record(ShipmentStatus.DELIVERED, null);

        assertThatThrownBy(() -> record(ShipmentStatus.CANCELED, null)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    private void record(ShipmentStatus status, String location) {
        tracking.record(operator.getId(), shipmentId, new RecordTrackingEventRequest(status, location));
        entityManager.flush();
        entityManager.clear();
    }
}
