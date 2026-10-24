package io.github.leonardodvf.deliverytracker.auth.controller;

import io.github.leonardodvf.deliverytracker.auth.dto.LoginRequest;
import io.github.leonardodvf.deliverytracker.auth.dto.TokenResponse;
import io.github.leonardodvf.deliverytracker.auth.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService login;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return login.login(request);
    }
}
