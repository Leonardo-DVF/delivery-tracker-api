package io.github.leonardodvf.deliverytracker.freight.entity;

import io.github.leonardodvf.deliverytracker.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "freight_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreightQuote {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @Column(nullable = false, updatable = false, length = 8)
    private String destinationPostalCode;

    @Column(nullable = false, updatable = false, precision = 7, scale = 3)
    private BigDecimal weightKg;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    public FreightQuote(User requester, String destinationPostalCode, BigDecimal weightKg, BigDecimal amount,
                        Instant createdAt, Duration ttl) {
        this.id = UUID.randomUUID();
        this.requester = requester;
        this.destinationPostalCode = destinationPostalCode;
        this.weightKg = weightKg;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(ttl);
    }

    public boolean isExpiredAt(Instant instant) {
        return !instant.isBefore(expiresAt);
    }
}
