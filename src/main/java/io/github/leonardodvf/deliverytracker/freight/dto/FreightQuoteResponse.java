package io.github.leonardodvf.deliverytracker.freight.dto;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FreightQuoteResponse(
        @Schema(description = "Identifier used to create a shipment with this locked price.")
        UUID quoteId,
        @Schema(example = "15.00", description = "Base price plus price per kilogram times weight, rounded HALF_UP to two decimals.")
        BigDecimal amount,
        @Schema(example = "BRL") String currency,
        @Schema(example = "01000000") String destinationPostalCode,
        @Schema(example = "2.000") BigDecimal weightKg,
        @Schema(description = "Instant after which the quote can no longer be used.") Instant expiresAt,
        @Schema(example = "Fictional demonstration tariff; not a real carrier quote.") String disclaimer) {

    public static FreightQuoteResponse from(FreightQuote quote) {
        return new FreightQuoteResponse(quote.getId(), quote.getAmount(), "BRL", quote.getDestinationPostalCode(),
                quote.getWeightKg(), quote.getExpiresAt(), "Fictional demonstration tariff; not a real carrier quote.");
    }
}
