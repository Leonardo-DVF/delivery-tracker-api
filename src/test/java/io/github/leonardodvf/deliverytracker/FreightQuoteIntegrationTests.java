package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class FreightQuoteIntegrationTests extends JwtTestSupport {

    @Autowired private FreightService freight;
    @Autowired private FreightQuoteRepository quotes;
    @Autowired private FreightRateRepository rates;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void persistsQuoteWithRequesterPriceAndDefaultThirtyMinuteExpiration() {
        var customer = users.saveAndFlush(new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER));

        var created = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        entityManager.flush();
        entityManager.clear();

        var stored = quotes.findById(created.getId()).orElseThrow();
        assertThat(stored.getRequester().getId()).isEqualTo(customer.getId());
        assertThat(stored.getDestinationPostalCode()).isEqualTo("01000000");
        assertThat(stored.getWeightKg()).isEqualByComparingTo("2.000");
        assertThat(stored.getAmount()).isEqualByComparingTo("15.00");
        assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void keepsQuotedAmountWhenTariffsChangeAfterwards() {
        var customer = users.saveAndFlush(new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER));
        var created = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        entityManager.flush();

        rates.deleteAll();
        rates.flush();
        entityManager.clear();

        assertThat(quotes.findById(created.getId()).orElseThrow().getAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void storesOperatorQuotesToo() {
        var operator = users.saveAndFlush(new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR));

        var created = freight.createQuote(operator.getId(), "60000000", new BigDecimal("1"));
        entityManager.flush();

        assertThat(quotes.findById(created.getId())).isPresent();
    }

    @Test
    void doesNotPersistQuoteWhenNoTariffApplies() {
        var customer = users.saveAndFlush(new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER));

        assertThatThrownBy(() -> freight.createQuote(customer.getId(), "01000000", new BigDecimal("31")))
                .isInstanceOf(FreightRateUnavailableException.class);
        assertThat(quotes.count()).isZero();
    }

    @Test
    void databaseRejectsQuoteThatExpiresBeforeItIsCreated() {
        var customer = users.saveAndFlush(new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO freight_quotes (id, requester_id, destination_postal_code, weight_kg, amount, created_at, expires_at)
                VALUES (?, ?, '01000000', 2, 15, now(), now() - interval '1 minute')
                """, UUID.randomUUID(), customer.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_freight_quotes_expiration");
    }

    @Test
    void databaseRejectsMalformedPostalCode() {
        var customer = users.saveAndFlush(new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO freight_quotes (id, requester_id, destination_postal_code, weight_kg, amount, created_at, expires_at)
                VALUES (?, ?, '01000-00', 2, 15, now(), now() + interval '30 minutes')
                """, UUID.randomUUID(), customer.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_freight_quotes_postal_code");
    }

    @Test
    void databaseRejectsUnknownRequester() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO freight_quotes (id, requester_id, destination_postal_code, weight_kg, amount, created_at, expires_at)
                VALUES (?, ?, '01000000', 2, 15, now(), now() + interval '30 minutes')
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
