package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the V7 invariants directly in PostgreSQL, bypassing the application code.
 * Each test ends with at most one failing statement, because a failed statement aborts the test transaction.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class TrackingEventConstraintIntegrationTests extends JwtTestSupport {

    @Autowired private ShipmentService shipments;
    @Autowired private FreightService freight;
    @Autowired private UserRepository users;
    @Autowired private TrackingEventRepository events;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    private UUID shipmentId;

    @BeforeEach
    void createShipment() {
        var customer = users.saveAndFlush(new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        shipmentId = shipments.create(customer.getId(), request(quote.getId())).id();
        entityManager.flush();
    }

    @Test
    void storesCarrierEventWithIdAndFindsItById() {
        insert("CARRIER", "evt_2026-11-19:0001.a");

        assertThat(events.existsByCarrierEventId("evt_2026-11-19:0001.a")).isTrue();
        assertThat(events.existsByCarrierEventId("evt_unknown")).isFalse();
    }

    @Test
    void rejectsTheSameCarrierEventIdTwice() {
        insert("CARRIER", "evt-1");

        assertThatThrownBy(() -> insert("CARRIER", "evt-1")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "source={0}, id={1}")
    @CsvSource(value = {"CARRIER,NULL", "OPERATOR,evt-2", "API,evt-3", "CARRIER,''", "CARRIER,evt 4", "CARRIER,evt/5"},
            nullValues = "NULL")
    void rejectsIdsOutsideCarrierEventsOrWithUnsafeCharacters(String source, String carrierEventId) {
        assertThatThrownBy(() -> insert(source, carrierEventId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsIdsLongerThan100Characters() {
        assertThatThrownBy(() -> insert("CARRIER", "e".repeat(101))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(String source, String carrierEventId) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO tracking_events (id, shipment_id, status, occurred_at, received_at, source, carrier_event_id)
                VALUES (?, ?, 'PICKED_UP', ?, ?, ?, ?)
                """, UUID.randomUUID(), shipmentId, now, now, source, carrierEventId);
    }
}
