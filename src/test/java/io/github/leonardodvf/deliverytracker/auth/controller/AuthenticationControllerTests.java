package io.github.leonardodvf.deliverytracker.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.github.leonardodvf.deliverytracker.JwtTestSupport;
import io.github.leonardodvf.deliverytracker.auth.config.JwtConfig;
import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.auth.service.JwtService;
import io.github.leonardodvf.deliverytracker.auth.service.LoginService;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.user.controller.UserController;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import io.github.leonardodvf.deliverytracker.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({LoginController.class, UserController.class})
@Import({SecurityConfig.class, JwtConfig.class, LoginService.class, JwtService.class,
        UserService.class, ApiExceptionHandler.class})
class AuthenticationControllerTests extends JwtTestSupport {

    private static final String PASSWORD = "test-password-2026";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @MockitoBean private UserRepository users;
    private User user;

    @BeforeEach
    void configureUser() {
        user = new User("Customer", "customer@example.test", PASSWORD_HASH, UserRole.CUSTOMER);
        stubUser();
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logsInWithRealSignatureAndUsesBearerTokenToReadProfile(UserRole role) throws Exception {
        user = new User("Customer", "customer@example.test", PASSWORD_HASH, role);
        stubUser();

        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", " CUSTOMER@EXAMPLE.TEST ", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var token = body.path("accessToken").asText();
        var jwt = decoder.decode(token);

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("delivery-tracker-test");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo(role.name());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse(body.path("expiresAt").asText()));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
        assertThat(jwt.getClaims().toString()).doesNotContain(user.getEmail(), PASSWORD, "passwordHash");

        var profile = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse();

        assertThat(json.readTree(profile.getContentAsString()).size()).isEqualTo(5);
        assertThat(profile.getCookie("JSESSIONID")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown@example.test", "customer@example.test"})
    void returnsSameSafeErrorForUnknownEmailAndWrongPassword(String email) throws Exception {
        var body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(email, "wrong-password", PASSWORD_HASH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "wrong-issuer", "missing-issuer", "invalid-subject", "missing-subject",
            "unknown-role", "missing-role", "multiple-roles", "missing-expiration", "missing-issued-at",
            "future-issued-at", "future-not-before"})
    void rejectsInvalidSignedClaimsBeforeLookingUpTheUser(String scenario) throws Exception {
        var claims = validClaims();
        switch (scenario) {
            case "expired" -> {
                claims.put("iat", Instant.now().minusSeconds(120));
                claims.put("exp", Instant.now().minusSeconds(1));
            }
            case "wrong-issuer" -> claims.put("iss", "other-issuer");
            case "missing-issuer" -> claims.remove("iss");
            case "invalid-subject" -> claims.put("sub", "not-a-uuid");
            case "missing-subject" -> claims.remove("sub");
            case "unknown-role" -> claims.put("role", "ADMIN");
            case "missing-role" -> claims.remove("role");
            case "multiple-roles" -> claims.put("role", List.of("CUSTOMER", "OPERATOR"));
            case "missing-expiration" -> claims.remove("exp");
            case "missing-issued-at" -> claims.remove("iat");
            case "future-issued-at" -> {
                claims.put("iat", Instant.now().plusSeconds(120));
                claims.put("exp", Instant.now().plusSeconds(3600));
            }
            case "future-not-before" -> claims.put("nbf", Instant.now().plusSeconds(120));
            default -> throw new IllegalArgumentException(scenario);
        }

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + sign(encoder, claims, SignatureAlgorithm.RS256)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(users, never()).findById(any());
    }

    @Test
    void rejectsTokenSignedByAnotherKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate()).build();
        var otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + sign(otherEncoder, validClaims(), SignatureAlgorithm.RS256)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDifferentSigningAlgorithmEvenWithTrustedKey() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + sign(encoder, validClaims(), SignatureAlgorithm.RS512)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedSignatureAndMissingOrMalformedTokens() throws Exception {
        var token = sign(encoder, validClaims(), SignatureAlgorithm.RS256);
        var parts = token.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        var tampered = String.join(".", parts);

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUnauthorizedWhenTokenUserNoLongerExists() throws Exception {
        when(users.findById(user.getId())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + sign(encoder, validClaims(), SignatureAlgorithm.RS256)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalid\",\"password\":\"test-password-2026\"}",
            "{\"email\":\"customer@example.test\"}",
            "{\"email\":\"customer@example.test\",\"password\":\"test-password-2026\",\"role\":\"OPERATOR\"}"})
    void rejectsInvalidLoginPayload(String payload) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(users, never()).findByEmail(any());
    }

    @Test
    void rejectsLoginPasswordExceedingUtf8Limit() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", "é".repeat(37)))))
                .andExpect(status().isBadRequest());
        verify(users, never()).findByEmail(any());
    }

    @Test
    void doesNotExposeOtherActuatorEndpointsToAuthenticatedUsers() throws Exception {
        mvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer " + sign(encoder, validClaims(), SignatureAlgorithm.RS256)))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> validClaims() {
        var claims = new HashMap<String, Object>();
        claims.put("iss", "delivery-tracker-test");
        claims.put("sub", user.getId().toString());
        claims.put("iat", Instant.now().minusSeconds(1));
        claims.put("exp", Instant.now().plusSeconds(3600));
        claims.put("role", user.getRole().name());
        return claims;
    }

    private String sign(JwtEncoder signer, Map<String, Object> values, SignatureAlgorithm algorithm) {
        var claims = JwtClaimsSet.builder().claims(target -> target.putAll(values)).build();
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

    private void stubUser() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }
}
