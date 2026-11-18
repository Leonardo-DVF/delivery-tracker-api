package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.*;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class ShipmentIntegrationTests extends JwtTestSupport {

    @Autowired private ShipmentService service;
    @Autowired private ShipmentRepository shipments;
    @Autowired private TrackingEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private FreightRateRepository rates;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsOwnerAddressesPriceAndInitialEventAndKeepsPriceAfterTariffRemoval() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));

        var response = service.create(customer.getId(), request());
        entityManager.flush();
        entityManager.clear();

        var stored = shipments.findById(response.id()).orElseThrow();
        assertThat(stored.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(stored.getOrigin().getPostalCode()).isEqualTo("20000000");
        assertThat(stored.getDestination().getPostalCode()).isEqualTo("01000000");
        assertThat(stored.getDestination().getComplement()).isEqualTo("Apt 1");
        assertThat(stored.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(stored.getWeightKg()).isEqualByComparingTo("2.000");
        assertThat(stored.getLengthCm()).isEqualByComparingTo("30.00");
        var history = events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(response.id());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getOccurredAt()).isEqualTo(stored.getCreatedAt());
        assertThat(history.getFirst().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(history.getFirst().getSource()).isEqualTo("API");
        rates.deleteAll();
        rates.flush();
        entityManager.clear();
        assertThat(shipments.findById(response.id()).orElseThrow().getFreightAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void doesNotPersistShipmentOrInitialEventWhenNoTariffApplies() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var original = request();
        var request = new CreateShipmentRequest(original.origin(), original.destination(), new BigDecimal("31"),
                original.lengthCm(), original.widthCm(), original.heightCm());

        assertThatThrownBy(() -> service.create(customer.getId(), request)).isInstanceOf(FreightRateUnavailableException.class);
        assertThat(shipments.count()).isZero();
        assertThat(events.count()).isZero();
    }
}
