package io.github.leonardodvf.deliverytracker.auth.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
}
