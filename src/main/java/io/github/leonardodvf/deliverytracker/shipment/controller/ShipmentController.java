package io.github.leonardodvf.deliverytracker.shipment.controller;

import io.github.leonardodvf.deliverytracker.shared.dto.PageResponse;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments")
@SecurityRequirement(name = "bearerAuth")
public class ShipmentController {

    private final ShipmentService shipments;

    @PostMapping
    @Operation(summary = "Create a shipment from a freight quote",
            description = "CUSTOMER only. The quote must belong to the caller, be unexpired, unused and match the destination "
                    + "postal code. Weight and freight amount are copied from the quote; ownership, tracking code and "
                    + "initial status are assigned by the server.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Shipment and initial event created atomically; Location points to it"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Only customers can create shipments"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/FreightQuoteNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/FreightQuoteAlreadyUsed"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/FreightQuoteUnusable")
    })
    public ResponseEntity<ShipmentResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateShipmentRequest request) {
        var created = shipments.create(UUID.fromString(jwt.getSubject()), request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List shipments",
            description = "CUSTOMER sees only their own shipments; OPERATOR sees all. Newest first, ties broken by id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of shipments"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    })
    public PageResponse<ShipmentResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Optional status filter") @RequestParam(required = false) ShipmentStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return shipments.list(UUID.fromString(jwt.getSubject()), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a shipment",
            description = "CUSTOMER can read only their own shipments; another customer's shipment returns 404. OPERATOR can read any.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment found"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound")
    })
    public ShipmentResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return shipments.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a shipment",
            description = "CUSTOMER can cancel their own shipment only while it is CREATED (before pickup). "
                    + "OPERATOR can cancel any shipment up to IN_TRANSIT. Records a CANCELED tracking event.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment canceled"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/InvalidStatusTransition")
    })
    public ShipmentResponse cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return shipments.cancel(UUID.fromString(jwt.getSubject()), id);
    }
}
