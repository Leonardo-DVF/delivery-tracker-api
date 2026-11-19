package io.github.leonardodvf.deliverytracker.tracking.entity;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackingEvent {

    public static final String SOURCE_API = "API";
    public static final String SOURCE_OPERATOR = "OPERATOR";
    public static final String SOURCE_CUSTOMER = "CUSTOMER";
    public static final String SOURCE_CARRIER = "CARRIER";

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private Instant receivedAt;
    @Column(nullable = false, length = 30)
    private String source;
    @Column(length = 150)
    private String location;
    @Column(length = 100, unique = true, updatable = false)
    private String carrierEventId;

    public static TrackingEvent initial(Shipment shipment) {
        return of(shipment, ShipmentStatus.CREATED, shipment.getCreatedAt(), shipment.getCreatedAt(), SOURCE_API, null);
    }

    /** Records the status the shipment has just moved to; call after {@link Shipment#transitionTo}. */
    public static TrackingEvent statusChange(Shipment shipment, String source, String location) {
        var at = shipment.getUpdatedAt();
        return of(shipment, shipment.getStatus(), at, at, source, location);
    }

    /**
     * Carrier event for the status the shipment has just moved to. occurredAt is the carrier's clock,
     * receivedAt is ours, so late deliveries stay visible.
     */
    public static TrackingEvent fromCarrier(Shipment shipment, String carrierEventId, Instant occurredAt, String location) {
        var event = of(shipment, shipment.getStatus(), occurredAt, shipment.getUpdatedAt(), SOURCE_CARRIER, location);
        event.carrierEventId = carrierEventId;
        return event;
    }

    private static TrackingEvent of(Shipment shipment, ShipmentStatus status, Instant occurredAt,
            Instant receivedAt, String source, String location) {
        var event = new TrackingEvent();
        event.id = UUID.randomUUID();
        event.shipment = shipment;
        event.status = status;
        event.occurredAt = occurredAt;
        event.receivedAt = receivedAt;
        event.source = source;
        event.location = location;
        return event;
    }
}
