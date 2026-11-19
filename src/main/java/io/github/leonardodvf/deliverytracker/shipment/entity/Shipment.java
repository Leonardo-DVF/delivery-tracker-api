package io.github.leonardodvf.deliverytracker.shipment.entity;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, unique = true, updatable = false)
    private FreightQuote quote;
    @Column(nullable = false, unique = true, updatable = false, length = 32)
    private String trackingCode;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "contactName", column = @Column(name = "origin_contact_name", nullable = false, length = 100)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "origin_postal_code", nullable = false, length = 8)),
        @AttributeOverride(name = "street", column = @Column(name = "origin_street", nullable = false, length = 150)),
        @AttributeOverride(name = "number", column = @Column(name = "origin_number", nullable = false, length = 20)),
        @AttributeOverride(name = "complement", column = @Column(name = "origin_complement", length = 100)),
        @AttributeOverride(name = "neighborhood", column = @Column(name = "origin_neighborhood", nullable = false, length = 100)),
        @AttributeOverride(name = "city", column = @Column(name = "origin_city", nullable = false, length = 100)),
        @AttributeOverride(name = "state", column = @Column(name = "origin_state", nullable = false, length = 2))
    })
    private Address origin;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "contactName", column = @Column(name = "destination_contact_name", nullable = false, length = 100)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "destination_postal_code", nullable = false, length = 8)),
        @AttributeOverride(name = "street", column = @Column(name = "destination_street", nullable = false, length = 150)),
        @AttributeOverride(name = "number", column = @Column(name = "destination_number", nullable = false, length = 20)),
        @AttributeOverride(name = "complement", column = @Column(name = "destination_complement", length = 100)),
        @AttributeOverride(name = "neighborhood", column = @Column(name = "destination_neighborhood", nullable = false, length = 100)),
        @AttributeOverride(name = "city", column = @Column(name = "destination_city", nullable = false, length = 100)),
        @AttributeOverride(name = "state", column = @Column(name = "destination_state", nullable = false, length = 2))
    })
    private Address destination;
    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal weightKg;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal lengthCm;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal widthCm;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal heightCm;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal freightAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public Shipment(User customer, FreightQuote quote, Address origin, Address destination,
            BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.trackingCode = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        this.customer = customer;
        this.quote = quote;
        this.origin = origin;
        this.destination = destination;
        this.weightKg = quote.getWeightKg();
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.freightAmount = quote.getAmount();
        this.status = ShipmentStatus.CREATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** The only way to change the status, so every caller (API, webhooks) obeys the same state machine. */
    public void transitionTo(ShipmentStatus next, Instant at) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(status, next);
        }
        if (at.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Status change cannot predate the last update");
        }
        this.status = next;
        this.updatedAt = at;
    }
}
