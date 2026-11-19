package io.github.leonardodvf.deliverytracker.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Event sent by the carrier. Unknown fields are ignored on purpose (tolerant reader): a carrier adding a
 * field to its payload must not make every delivery fail, unlike the strict public API requests.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CarrierEventPayload(
        @Schema(description = "Carrier's unique event id, used to discard redeliveries", example = "evt_20261119_0001")
        @NotNull @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}") String eventId,
        @Schema(description = "Tracking code of the shipment, case-insensitive", example = "3F2A9C1B7D4E4F0A8B6C5D2E1F0A9B8C")
        @NotNull @Pattern(regexp = "[0-9A-Fa-f]{32}") String trackingCode,
        @NotNull ShipmentStatus status,
        @Schema(description = "When the event happened, by the carrier's clock")
        @NotNull Instant occurredAt,
        @Schema(description = "Optional free-text location. Blank becomes null.", maxLength = 150)
        @Size(max = 150) String location) {

    public CarrierEventPayload {
        location = location == null || location.isBlank() ? null : location.strip();
    }
}
