package io.github.leonardodvf.deliverytracker.auth.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "RS256 JWT to send in the Authorization header.", format = "jwt") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Token lifetime in seconds.", example = "3600") long expiresIn,
        @Schema(example = "2026-01-15T13:00:00Z") Instant expiresAt) {
}
