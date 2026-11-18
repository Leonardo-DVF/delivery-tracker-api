package io.github.leonardodvf.deliverytracker.shipment.dto;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(UUID id, String trackingCode, UUID quoteId, AddressDto origin, AddressDto destination,
        BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
        BigDecimal freightAmount, ShipmentStatus status, Instant createdAt, Instant updatedAt) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(shipment.getId(), shipment.getTrackingCode(), shipment.getQuote().getId(),
                AddressDto.from(shipment.getOrigin()), AddressDto.from(shipment.getDestination()),
                shipment.getWeightKg(), shipment.getLengthCm(), shipment.getWidthCm(), shipment.getHeightCm(),
                shipment.getFreightAmount(), shipment.getStatus(), shipment.getCreatedAt(), shipment.getUpdatedAt());
    }
}
