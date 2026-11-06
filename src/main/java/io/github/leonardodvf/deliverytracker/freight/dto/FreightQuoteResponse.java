package io.github.leonardodvf.deliverytracker.freight.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record FreightQuoteResponse(
        @Schema(example = "15.00", description = "Base price plus price per kilogram times weight, rounded HALF_UP to two decimals.")
        BigDecimal amount,
        @Schema(example = "BRL") String currency,
        @Schema(example = "Fictional demonstration tariff; not a real carrier quote.") String disclaimer) {
}
