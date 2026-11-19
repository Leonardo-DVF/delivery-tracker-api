package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.CancellationNotAllowedException;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
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
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class ShipmentCancellationIntegrationTests extends JwtTestSupport {

    @Autowired private ShipmentService service;
    @Autowired private TrackingService tracking;
    @Autowired private FreightService freight;
    @Autowired private ShipmentRepository shipments;
    @Autowired private TrackingEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    private User alice;
    private User bob;
    private User operator;

    @BeforeEach
    void createUsers() {
        alice = users.saveAndFlush(new User("Alice", "alice@example.test", "hash", UserRole.CUSTOMER));
        bob = users.saveAndFlush(new User("Bob", "bob@example.test", "hash", UserRole.CUSTOMER));
        operator = users.saveAndFlush(new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR));
    }

    @Test
    void customerCancelsOwnShipmentBeforePickup() {
        var id = createFor(alice);

        assertThat(service.cancel(alice.getId(), id).status()).isEqualTo(ShipmentStatus.CANCELED);
        entityManager.flush();
        entityManager.clear();

        assertThat(shipments.findById(id).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CANCELED);
        assertThat(events.findByShipmentIdOrderByReceivedAtAscOccurredAtAsc(id))
                .extracting(TrackingEvent::getStatus, TrackingEvent::getSource)
                .containsExactly(
                        tuple(ShipmentStatus.CREATED, "API"),
                        tuple(ShipmentStatus.CANCELED, "CUSTOMER"));
    }

    @Test
    void customerCannotCancelAfterPickupNorCancelSomeoneElsesShipment() {
        var picked = createFor(alice);
        advance(picked, ShipmentStatus.PICKED_UP);
        var bobs = createFor(bob);

        assertThatThrownBy(() -> service.cancel(alice.getId(), picked)).isInstanceOf(CancellationNotAllowedException.class);
        assertThatThrownBy(() -> service.cancel(alice.getId(), bobs)).isInstanceOf(ShipmentNotFoundException.class);
        entityManager.clear();
        assertThat(shipments.findById(bobs).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CREATED);
    }

    @Test
    void operatorCancelsUntilInTransitButNotOnceOutForDelivery() {
        var inTransit = createFor(alice);
        advance(inTransit, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT);
        var outForDelivery = createFor(bob);
        advance(outForDelivery, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY);

        assertThat(service.cancel(operator.getId(), inTransit).status()).isEqualTo(ShipmentStatus.CANCELED);
        assertThatThrownBy(() -> service.cancel(operator.getId(), outForDelivery))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> service.cancel(operator.getId(), inTransit))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private UUID createFor(User customer) {
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        var id = service.create(customer.getId(), request(quote.getId())).id();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private void advance(UUID shipmentId, ShipmentStatus... statuses) {
        for (var status : statuses) {
            tracking.record(operator.getId(), shipmentId, new RecordTrackingEventRequest(status, null));
            entityManager.flush();
            entityManager.clear();
        }
    }
}
