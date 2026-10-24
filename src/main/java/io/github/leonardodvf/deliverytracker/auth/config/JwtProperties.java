package io.github.leonardodvf.deliverytracker.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank String issuer, @NotNull Resource privateKey,
                            @NotNull Resource publicKey, @Min(60) @Max(86400) long ttlSeconds) {
}
