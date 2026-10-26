package io.github.leonardodvf.deliverytracker.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RegisterRequest(
        @Schema(example = "Customer") @NotBlank @Size(max = 100) String name,
        @Schema(example = "customer@example.test") @NotBlank @Email @Size(max = 254) String email,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "12 to 72 characters and at most 72 UTF-8 bytes.", example = "example-password-2026")
        @NotBlank @Size(min = 12, max = 72) String password) {

    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @AssertTrue(message = "must contain at most 72 UTF-8 bytes")
    @Schema(hidden = true)
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
