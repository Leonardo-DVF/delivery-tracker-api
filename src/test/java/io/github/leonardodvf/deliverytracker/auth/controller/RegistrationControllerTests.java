package io.github.leonardodvf.deliverytracker.auth.controller;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.auth.dto.RegisterRequest;
import io.github.leonardodvf.deliverytracker.auth.exception.EmailAlreadyRegisteredException;
import io.github.leonardodvf.deliverytracker.auth.service.RegistrationService;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.user.dto.UserResponse;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class RegistrationControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private RegistrationService registration;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void allowsAnonymousRegistrationAndReturnsOnlyPublicUserFields() throws Exception {
        var id = UUID.randomUUID();
        when(registration.register(any())).thenReturn(
                new UserResponse(id, "Customer", "customer@example.test", UserRole.CUSTOMER, Instant.now()));

        var result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(" Customer ", " CUSTOMER@Example.test ", "test-password-2026")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();

        assertThat(json.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(5);
        verify(registration).register(new RegisterRequest("Customer", "customer@example.test", "test-password-2026"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputWithoutCallingService(String body) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("test-password-2026", "passwordHash", "stackTrace");
        verifyNoInteractions(registration);
    }

    @Test
    void acceptsPasswordWithExactly72Utf8Bytes() throws Exception {
        when(registration.register(any())).thenReturn(new UserResponse(UUID.randomUUID(), "Customer",
                "customer@example.test", UserRole.CUSTOMER, Instant.now()));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Customer", "customer@example.test", "é".repeat(36))))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsConflictForExistingEmail() throws Exception {
        when(registration.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Customer", "customer@example.test", "test-password-2026")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void returnsSafeConflictForConcurrentDuplicateAtDatabase() throws Exception {
        when(registration.register(any())).thenThrow(new DataIntegrityViolationException("private database details"));

        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Customer", "customer@example.test", "test-password-2026")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("private database details");
    }

    @Test
    void deniesOtherEndpointsWithoutStartingASession() throws Exception {
        var response = mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse();

        assertThat(response.getCookie("JSESSIONID")).isNull();
    }

    private String body(String name, String email, String password) {
        return json.writeValueAsString(Map.of("name", name, "email", email, "password", password));
    }

    static Stream<String> invalidRequests() {
        var valid = "\"name\":\"Customer\",\"email\":\"customer@example.test\",\"password\":\"test-password-2026\"";
        return Stream.of(
                "{}",
                "{\"name\":\" \",\"email\":\"customer@example.test\",\"password\":\"test-password-2026\"}",
                "{\"name\":\"Customer\",\"email\":\"invalid\",\"password\":\"test-password-2026\"}",
                "{\"name\":\"Customer\",\"email\":\"customer@example.test\",\"password\":\"short\"}",
                "{\"name\":\"Customer\",\"email\":\"customer@example.test\",\"password\":\"" + "a".repeat(73) + "\"}",
                "{\"name\":\"Customer\",\"email\":\"customer@example.test\",\"password\":\"" + "é".repeat(37) + "\"}",
                "{" + valid + ",\"role\":\"OPERATOR\"}",
                "{" + valid + ",\"role\":\"CUSTOMER\"}",
                "{" + valid + ",\"passwordHash\":\"injected\"}",
                "{invalid-json");
    }
}
