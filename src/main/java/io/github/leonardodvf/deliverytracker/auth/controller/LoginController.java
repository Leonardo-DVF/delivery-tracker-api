package io.github.leonardodvf.deliverytracker.auth.controller;

import io.github.leonardodvf.deliverytracker.auth.dto.LoginRequest;
import io.github.leonardodvf.deliverytracker.auth.dto.TokenResponse;
import io.github.leonardodvf.deliverytracker.auth.service.LoginService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class LoginController {

    private final LoginService login;

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a signed RS256 JWT. Unknown email and incorrect password produce the same error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bearer token issued"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return login.login(request);
    }
}
