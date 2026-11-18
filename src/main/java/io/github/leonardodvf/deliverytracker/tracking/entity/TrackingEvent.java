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

    public static TrackingEvent initial(Shipment shipment) {
        var event = new TrackingEvent();
        event.id = UUID.randomUUID();
        event.shipment = shipment;
        event.status = ShipmentStatus.CREATED;
        event.occurredAt = shipment.getCreatedAt();
        event.receivedAt = shipment.getCreatedAt();
        event.source = "API";
        return event;
    }
}
