package io.github.leonardodvf.deliverytracker.tracking.dto;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import java.time.Instant;
import java.util.List;

/**
 * Anonymous view of a shipment. Deliberately a separate DTO with no ids, names, addresses,
 * customer data or internal event sources, so the public endpoint cannot leak them by accident.
 */
public record PublicTrackingResponse(String trackingCode, ShipmentStatus status, String destinationCity,
        String destinationState, Instant updatedAt, List<Event> events) {

    public record Event(ShipmentStatus status, Instant occurredAt, String location) {
    }

    public static PublicTrackingResponse from(Shipment shipment, List<TrackingEvent> history) {
        return new PublicTrackingResponse(shipment.getTrackingCode(), shipment.getStatus(),
                shipment.getDestination().getCity(), shipment.getDestination().getState(), shipment.getUpdatedAt(),
                history.stream().map(event -> new Event(event.getStatus(), event.getOccurredAt(), event.getLocation())).toList());
    }
}
