package io.github.leonardodvf.deliverytracker.freight.entity;

import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FreightQuoteTests {

    private final Instant createdAt = Instant.parse("2026-11-18T12:00:00Z");
    private final FreightQuote quote = new FreightQuote(
            new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER),
            "01000000", new BigDecimal("2"), new BigDecimal("15.00"), createdAt, Duration.ofMinutes(30));

    @Test
    void expiresExactlyAtCreationPlusTimeToLive() {
        assertThat(quote.getExpiresAt()).isEqualTo(Instant.parse("2026-11-18T12:30:00Z"));
        assertThat(quote.isExpiredAt(createdAt)).isFalse();
        assertThat(quote.isExpiredAt(quote.getExpiresAt().minusNanos(1))).isFalse();
        assertThat(quote.isExpiredAt(quote.getExpiresAt())).isTrue();
        assertThat(quote.isExpiredAt(quote.getExpiresAt().plusSeconds(1))).isTrue();
    }
}
