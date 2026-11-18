package io.github.leonardodvf.deliverytracker.shipment.controller;

import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments")
@SecurityRequirement(name = "bearerAuth")
public class ShipmentController {

    private final ShipmentService shipments;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shipment", description = "CUSTOMER only. Ownership, tracking code, freight and initial status are assigned by the server.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Shipment and initial event created atomically"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Only customers can create shipments"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/FreightUnavailable")
    })
    public ShipmentResponse create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateShipmentRequest request) {
        return shipments.create(UUID.fromString(jwt.getSubject()), request);
    }
}
