package io.github.leonardodvf.deliverytracker.freight.controller;

import io.github.leonardodvf.deliverytracker.freight.dto.FreightQuoteRequest;
import io.github.leonardodvf.deliverytracker.freight.dto.FreightQuoteResponse;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/freight")
@RequiredArgsConstructor
@Tag(name = "Freight")
@SecurityRequirement(name = "bearerAuth")
public class FreightController {

    private final FreightService freight;

    @PostMapping("/quotes")
    @Operation(summary = "Quote fictional freight", description = "Available to CUSTOMER and OPERATOR. Uses destination postal code and actual weight only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fictional quote calculated in BRL"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/FreightTariffConflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/FreightUnavailable")
    })
    public FreightQuoteResponse quote(@Valid @RequestBody FreightQuoteRequest request) {
        return new FreightQuoteResponse(freight.calculateAmount(request.destinationPostalCode(), request.weightKg()),
                "BRL", "Fictional demonstration tariff; not a real carrier quote.");
    }
}
