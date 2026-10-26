package io.github.leonardodvf.deliverytracker.user.controller;

import io.github.leonardodvf.deliverytracker.user.dto.UserResponse;
import io.github.leonardodvf.deliverytracker.user.service.UserService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService users;

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user", description = "Available to CUSTOMER and OPERATOR. The user is identified by the validated token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated user profile"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    })
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return users.me(UUID.fromString(jwt.getSubject()));
    }
}
