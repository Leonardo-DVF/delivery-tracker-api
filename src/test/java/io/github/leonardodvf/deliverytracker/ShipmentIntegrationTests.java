package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteAlreadyUsedException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteExpiredException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.*;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.quote;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class ShipmentIntegrationTests extends JwtTestSupport {

    @Autowired private ShipmentService service;
    @Autowired private FreightService freight;
    @Autowired private ShipmentRepository shipments;
    @Autowired private TrackingEventRepository events;
    @Autowired private FreightQuoteRepository quotes;
    @Autowired private UserRepository users;
    @Autowired private FreightRateRepository rates;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsShipmentFromQuoteWithInitialEventAndKeepsQuotedPriceAfterTariffRemoval() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));

        var response = service.create(customer.getId(), request(quote.getId()));
        entityManager.flush();
        entityManager.clear();

        var stored = shipments.findById(response.id()).orElseThrow();
        assertThat(stored.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(stored.getQuote().getId()).isEqualTo(quote.getId());
        assertThat(stored.getOrigin().getPostalCode()).isEqualTo("20000000");
        assertThat(stored.getDestination().getPostalCode()).isEqualTo("01000000");
        assertThat(stored.getDestination().getComplement()).isEqualTo("Apt 1");
        assertThat(stored.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(stored.getWeightKg()).isEqualByComparingTo("2.000");
        assertThat(stored.getLengthCm()).isEqualByComparingTo("30.00");
        var history = events.findByShipmentIdOrderByReceivedAtAscOccurredAtAsc(response.id());
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
    void rejectsSecondShipmentForTheSameQuote() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        service.create(customer.getId(), request(quote.getId()));

        assertThatThrownBy(() -> service.create(customer.getId(), request(quote.getId())))
                .isInstanceOf(FreightQuoteAlreadyUsedException.class);
        assertThat(shipments.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void rejectsExpiredQuoteWithoutPersistingAnything() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var expired = quotes.saveAndFlush(quote(customer, "01000000", Instant.now().minusSeconds(3600)));

        assertThatThrownBy(() -> service.create(customer.getId(), request(expired.getId())))
                .isInstanceOf(FreightQuoteExpiredException.class);
        assertThat(shipments.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    void generatesDistinctTrackingCodesForDistinctShipments() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var first = service.create(customer.getId(),
                request(freight.createQuote(customer.getId(), "01000000", new BigDecimal("2")).getId()));
        var second = service.create(customer.getId(),
                request(freight.createQuote(customer.getId(), "01000000", new BigDecimal("2")).getId()));

        assertThat(first.trackingCode()).isNotEqualTo(second.trackingCode()).matches("[0-9A-F]{32}");
    }

    @Test
    void databaseRejectsTwoShipmentsReferencingTheSameQuote() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        var created = service.create(customer.getId(), request(quote.getId()));
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shipments SELECT ?, customer_id, ?, origin_contact_name, origin_postal_code, origin_street,
                    origin_number, origin_complement, origin_neighborhood, origin_city, origin_state,
                    destination_contact_name, destination_postal_code, destination_street, destination_number,
                    destination_complement, destination_neighborhood, destination_city, destination_state,
                    weight_kg, length_cm, width_cm, height_cm, freight_amount, status, created_at, updated_at,
                    version, quote_id
                FROM shipments WHERE id = ?
                """, UUID.randomUUID(), "ABCDEF0123456789ABCDEF0123456789", created.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_shipments_quote");
    }

    @Test
    void databaseRejectsStatusesOutsideTheLifecycle() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var created = service.create(customer.getId(),
                request(freight.createQuote(customer.getId(), "01000000", new BigDecimal("2")).getId()));
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'POSTED' WHERE id = ?", created.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_shipments_status");
    }
}
