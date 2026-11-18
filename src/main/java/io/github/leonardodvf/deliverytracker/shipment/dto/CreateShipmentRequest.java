package io.github.leonardodvf.deliverytracker.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateShipmentRequest(
        @NotNull @Valid AddressDto origin,
        @NotNull @Valid AddressDto destination,
        @NotNull @DecimalMin("0.001") @DecimalMax("1000") @Digits(integer = 4, fraction = 3) BigDecimal weightKg,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal lengthCm,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal widthCm,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal heightCm) {
}
