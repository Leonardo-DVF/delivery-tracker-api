package io.github.leonardodvf.deliverytracker.freight.controller;

import io.github.leonardodvf.deliverytracker.freight.dto.FreightQuoteRequest;
import io.github.leonardodvf.deliverytracker.freight.dto.FreightQuoteResponse;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/freight")
@RequiredArgsConstructor
@Tag(name = "Freight")
@SecurityRequirement(name = "bearerAuth")
public class FreightController {

    private final FreightService freight;

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Quote fictional freight",
            description = "Available to CUSTOMER and OPERATOR. Uses destination postal code and actual weight only. "
                    + "The quote is stored for the requester and expires after the configured time.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fictional quote calculated in BRL and stored"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/FreightTariffConflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/FreightUnavailable")
    })
    public FreightQuoteResponse quote(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody FreightQuoteRequest request) {
        return FreightQuoteResponse.from(freight.createQuote(UUID.fromString(jwt.getSubject()),
                request.destinationPostalCode(), request.weightKg()));
    }
}
