package io.github.leonardodvf.deliverytracker.user.dto;

import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        @Schema(example = "a43a7dd1-bd1a-4fe8-bc84-a75e46788837") UUID id,
        @Schema(example = "Customer") String name,
        @Schema(example = "customer@example.test") String email,
        @Schema(example = "CUSTOMER") UserRole role,
        @Schema(example = "2026-01-15T12:00:00Z") Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
