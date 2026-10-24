package io.github.leonardodvf.deliverytracker.auth.service;

import io.github.leonardodvf.deliverytracker.auth.config.JwtProperties;
import io.github.leonardodvf.deliverytracker.auth.dto.TokenResponse;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenResponse issue(User user) {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = now.plusSeconds(properties.ttlSeconds());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttlSeconds(), expiresAt);
    }
}
