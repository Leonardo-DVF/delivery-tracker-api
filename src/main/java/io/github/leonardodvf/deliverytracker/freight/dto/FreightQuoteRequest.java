package io.github.leonardodvf.deliverytracker.freight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record FreightQuoteRequest(
        @Schema(example = "01000000", description = "Destination postal code with exactly eight digits.")
        @NotNull @Pattern(regexp = "[0-9]{8}") String destinationPostalCode,
        @Schema(example = "2.000", description = "Actual weight in kilograms, up to three decimal places. Cubic weight is not used.")
        @NotNull @DecimalMin("0.001") @DecimalMax("1000") @Digits(integer = 4, fraction = 3) BigDecimal weightKg) {
}
