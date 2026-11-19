package io.github.leonardodvf.deliverytracker.tracking.dto;

import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import java.time.Instant;
import java.util.UUID;

public record TrackingEventResponse(UUID id, ShipmentStatus status, Instant occurredAt, Instant receivedAt,
        String source, String location) {

    public static TrackingEventResponse from(TrackingEvent event) {
        return new TrackingEventResponse(event.getId(), event.getStatus(), event.getOccurredAt(), event.getReceivedAt(),
                event.getSource(), event.getLocation());
    }
}
