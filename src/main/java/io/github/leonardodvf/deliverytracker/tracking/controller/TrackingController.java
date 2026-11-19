package io.github.leonardodvf.deliverytracker.tracking.controller;

import io.github.leonardodvf.deliverytracker.tracking.dto.PublicTrackingResponse;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.dto.TrackingEventResponse;
import io.github.leonardodvf.deliverytracker.tracking.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tracking")
public class TrackingController {

    private final TrackingService tracking;

    @PostMapping("/shipments/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Record a tracking event",
            description = "OPERATOR only. Moves the shipment to the given status and appends an event, atomically. "
                    + "Allowed: CREATED→PICKED_UP→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED, and CANCELED before "
                    + "OUT_FOR_DELIVERY. Event time is the server time.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Status changed and event recorded"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Only operators can record tracking events"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/InvalidStatusTransition")
    })
    public TrackingEventResponse record(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id, @Valid @RequestBody RecordTrackingEventRequest request) {
        return tracking.record(UUID.fromString(jwt.getSubject()), id, request);
    }

    @GetMapping("/shipments/{id}/events")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a shipment's tracking history",
            description = "All events, oldest first. CUSTOMER can read only their own shipments; another customer's "
                    + "shipment returns 404. OPERATOR can read any.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tracking history"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound")
    })
    public List<TrackingEventResponse> history(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return tracking.history(UUID.fromString(jwt.getSubject()), id);
    }

    @GetMapping("/tracking/{trackingCode}")
    @Operation(summary = "Track a shipment by code (public)",
            description = "No authentication. Returns the current status, destination city/state and the event "
                    + "timeline, without names, addresses or customer data. The code is case-insensitive.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment found"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound")
    })
    public PublicTrackingResponse publicTracking(
            @Parameter(description = "32 hexadecimal characters", example = "3F2A9C1B7D4E4F0A8B6C5D2E1F0A9B8C")
            @PathVariable @Pattern(regexp = "[0-9A-Fa-f]{32}") String trackingCode) {
        return tracking.publicTracking(trackingCode);
    }
}
