package io.github.leonardodvf.deliverytracker.auth.service;

import io.github.leonardodvf.deliverytracker.auth.dto.LoginRequest;
import io.github.leonardodvf.deliverytracker.auth.dto.TokenResponse;
import io.github.leonardodvf.deliverytracker.auth.exception.InvalidCredentialsException;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email());
        var matches = passwords.matches(request.password(), user.map(value -> value.getPasswordHash()).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }
}
