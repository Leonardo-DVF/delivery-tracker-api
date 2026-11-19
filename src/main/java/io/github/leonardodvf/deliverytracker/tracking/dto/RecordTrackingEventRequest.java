package io.github.leonardodvf.deliverytracker.tracking.dto;

import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RecordTrackingEventRequest(
        @Schema(description = "Next status; must be a valid transition from the current one.")
        @NotNull ShipmentStatus status,
        @Schema(description = "Optional free-text location, such as a hub or city. Blank becomes null.", maxLength = 150)
        @Size(max = 150) String location) {

    public RecordTrackingEventRequest {
        location = location == null || location.isBlank() ? null : location.strip();
    }
}
