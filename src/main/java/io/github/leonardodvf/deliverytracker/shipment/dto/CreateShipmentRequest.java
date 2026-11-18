package io.github.leonardodvf.deliverytracker.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateShipmentRequest(
        @Schema(description = "Unexpired quote owned by the caller. Weight and freight amount come from it.")
        @NotNull UUID quoteId,
        @NotNull @Valid AddressDto origin,
        @Schema(description = "Postal code must match the quoted destination.")
        @NotNull @Valid AddressDto destination,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal lengthCm,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal widthCm,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal heightCm) {
}
