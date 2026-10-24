package io.github.leonardodvf.deliverytracker.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    JwtKeys jwtKeys(JwtProperties properties) throws Exception {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (var input = properties.privateKey().getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(input);
        }
        try (var input = properties.publicKey().getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(input);
        }
        if (privateKey == null || publicKey == null || publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException("JWT requires matching RSA keys with at least 2048 bits");
        }
        return new JwtKeys(privateKey, publicKey);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        var key = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, JwtProperties properties) {
        var decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(properties.issuer()),
                this::validateApplicationClaims));
        return decoder;
    }

    private OAuth2TokenValidatorResult validateApplicationClaims(Jwt jwt) {
        try {
            var subject = jwt.getSubject();
            if (subject == null || !UUID.fromString(subject).toString().equals(subject)
                    || !Set.of("CUSTOMER", "OPERATOR").contains(jwt.getClaimAsString("role"))
                    || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || jwt.getIssuedAt().isAfter(java.time.Instant.now())
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                return invalidClaims();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalidClaims();
        }
    }

    private OAuth2TokenValidatorResult invalidClaims() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
    }

    record JwtKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
    }
}
