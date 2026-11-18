package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class ShipmentQueryIntegrationTests extends JwtTestSupport {

    private static final Instant BASE = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired private ShipmentService service;
    @Autowired private FreightService freight;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private User alice;
    private User bob;
    private User operator;
    private UUID aliceOldest;
    private UUID aliceMiddle;
    private UUID aliceNewest;
    private UUID bobOnly;

    @BeforeEach
    void createShipmentsForTwoCustomers() {
        alice = users.saveAndFlush(new User("Alice", "alice@example.test", "hash", UserRole.CUSTOMER));
        bob = users.saveAndFlush(new User("Bob", "bob@example.test", "hash", UserRole.CUSTOMER));
        operator = users.saveAndFlush(new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR));
        aliceOldest = createAt(alice, BASE);
        aliceMiddle = createAt(alice, BASE.plusSeconds(60));
        aliceNewest = createAt(alice, BASE.plusSeconds(120));
        bobOnly = createAt(bob, BASE.plusSeconds(90));
        entityManager.clear();
    }

    @Test
    void customerListsOnlyOwnShipmentsNewestFirst() {
        var page = service.list(alice.getId(), null, 0, 20);

        assertThat(page.content()).extracting(ShipmentResponse::id).containsExactly(aliceNewest, aliceMiddle, aliceOldest);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(service.list(bob.getId(), null, 0, 20).content()).extracting(ShipmentResponse::id)
                .containsExactly(bobOnly);
    }

    @Test
    void operatorListsEveryCustomersShipments() {
        var page = service.list(operator.getId(), null, 0, 20);

        assertThat(page.content()).extracting(ShipmentResponse::id)
                .containsExactly(aliceNewest, bobOnly, aliceMiddle, aliceOldest);
    }

    @Test
    void paginatesWithAccurateTotals() {
        var first = service.list(alice.getId(), null, 0, 2);
        var second = service.list(alice.getId(), null, 1, 2);

        assertThat(first.content()).extracting(ShipmentResponse::id).containsExactly(aliceNewest, aliceMiddle);
        assertThat(second.content()).extracting(ShipmentResponse::id).containsExactly(aliceOldest);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(service.list(alice.getId(), null, 5, 2).content()).isEmpty();
    }

    @Test
    void breaksCreationTimeTiesByIdSoPagesNeverOverlap() {
        jdbc.update("UPDATE shipments SET created_at = ?, updated_at = ? WHERE customer_id = ?",
                Timestamp.from(BASE), Timestamp.from(BASE), alice.getId());

        var first = service.list(alice.getId(), null, 0, 2).content();
        var second = service.list(alice.getId(), null, 1, 2).content();

        var ids = new java.util.ArrayList<UUID>();
        first.forEach(item -> ids.add(item.id()));
        second.forEach(item -> ids.add(item.id()));
        assertThat(ids).doesNotHaveDuplicates().containsExactlyInAnyOrder(aliceNewest, aliceMiddle, aliceOldest);
        assertThat(ids).isSortedAccordingTo(java.util.Comparator.comparing(UUID::toString));
    }

    @Test
    void statusFilterKeepsCustomerScope() {
        jdbc.update("UPDATE shipments SET status = 'IN_TRANSIT' WHERE id IN (?, ?)", aliceMiddle, bobOnly);

        assertThat(service.list(alice.getId(), ShipmentStatus.IN_TRANSIT, 0, 20).content())
                .extracting(ShipmentResponse::id).containsExactly(aliceMiddle);
        assertThat(service.list(operator.getId(), ShipmentStatus.IN_TRANSIT, 0, 20).content())
                .extracting(ShipmentResponse::id).containsExactlyInAnyOrder(aliceMiddle, bobOnly);
        assertThat(service.list(alice.getId(), ShipmentStatus.CREATED, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void customerCannotReadAnotherCustomersShipmentButOperatorCan() {
        assertThat(service.get(alice.getId(), aliceOldest).id()).isEqualTo(aliceOldest);
        assertThatThrownBy(() -> service.get(alice.getId(), bobOnly)).isInstanceOf(ShipmentNotFoundException.class);
        assertThatThrownBy(() -> service.get(alice.getId(), UUID.randomUUID())).isInstanceOf(ShipmentNotFoundException.class);
        assertThat(service.get(operator.getId(), bobOnly).id()).isEqualTo(bobOnly);
    }

    private UUID createAt(User customer, Instant createdAt) {
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        var id = service.create(customer.getId(), request(quote.getId())).id();
        entityManager.flush();
        jdbc.update("UPDATE shipments SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt), id);
        return id;
    }
}
