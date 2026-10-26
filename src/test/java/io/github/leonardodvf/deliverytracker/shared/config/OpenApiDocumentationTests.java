package io.github.leonardodvf.deliverytracker.shared.config;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.auth.controller.LoginController;
import io.github.leonardodvf.deliverytracker.auth.controller.RegistrationController;
import io.github.leonardodvf.deliverytracker.auth.service.LoginService;
import io.github.leonardodvf.deliverytracker.auth.service.RegistrationService;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.user.controller.UserController;
import io.github.leonardodvf.deliverytracker.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OpenApiDocumentationTests.TestApplication.class)
@AutoConfigureMockMvc
class OpenApiDocumentationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private RegistrationService registration;
    @MockitoBean private LoginService login;
    @MockitoBean private UserService users;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void exposesOnlyCurrentApiOperationsWithoutAuthentication() throws Exception {
        var spec = specification();

        assertThat(spec.path("info").path("title").asText()).isEqualTo("Delivery Tracker API");
        assertThat(spec.path("paths").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/users/me");
        verifyNoInteractions(registration, login, users, decoder);
    }

    @Test
    void documentsBearerOnlyOnProtectedEndpointAndHidesInjectedPrincipal() throws Exception {
        var spec = specification();
        var scheme = spec.at("/components/securitySchemes/bearerAuth");

        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(spec.path("security").size()).isZero();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1register/post/security").size()).isZero();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1login/post/security").size()).isZero();
        assertThat(spec.at("/paths/~1api~1v1~1users~1me/get/security/0").has("bearerAuth")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1users~1me/get/parameters").size()).isZero();
    }

    @Test
    void describesStrictRequestFieldsValidationAndWriteOnlyPasswords() throws Exception {
        var schemas = specification().at("/components/schemas");
        var registrationSchema = schemas.path("RegisterRequest");
        var loginSchema = schemas.path("LoginRequest");

        assertThat(registrationSchema.path("properties").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("name", "email", "password");
        assertThat(loginSchema.path("properties").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("email", "password");
        assertThat(registrationSchema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(loginSchema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(registrationSchema.at("/properties/password/writeOnly").asBoolean()).isTrue();
        assertThat(registrationSchema.at("/properties/password/format").asText()).isEqualTo("password");
        assertThat(registrationSchema.at("/properties/password/minLength").asInt()).isEqualTo(12);
        assertThat(registrationSchema.at("/properties/password/maxLength").asInt()).isEqualTo(72);
        assertThat(registrationSchema.at("/properties/name/maxLength").asInt()).isEqualTo(100);
        assertThat(registrationSchema.path("required").size()).isEqualTo(3);
    }

    @Test
    void exposesOnlyDtosAndDocumentsProblemDetailsResponses() throws Exception {
        var spec = specification();
        var schemas = spec.at("/components/schemas");

        assertThat(schemas.path("UserResponse").path("properties").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("id", "name", "email", "role", "createdAt");
        assertThat(schemas.path("TokenResponse").path("properties").size()).isEqualTo(4);
        assertThat(schemas.has("User")).isFalse();
        assertThat(schemas.has("Jwt")).isFalse();
        assertThat(spec.toString()).doesNotContain("passwordHash", "privateKey", "LOCAL_OPERATOR_PASSWORD");
        assertThat(spec.at("/paths/~1api~1v1~1auth~1register/post/responses/201/content/application~1json/schema/$ref").asText())
                .endsWith("/UserResponse");
        assertThat(spec.at("/paths/~1api~1v1~1auth~1register/post/responses/409/$ref").asText())
                .isEqualTo("#/components/responses/Conflict");
        assertThat(spec.at("/paths/~1api~1v1~1auth~1login/post/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(spec.at("/components/responses/BadRequest/content/application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ApiProblem");
        assertThat(spec.at("/components/responses/BadRequest/content/application~1problem+json/example/status").asInt())
                .isEqualTo(400);
    }

    @Test
    void servesSwaggerUiAndItsAssetsWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistAuthorization").value(false));
    }

    @Test
    void servesYamlAndKeepsProfileProtectedAndDocumentationReadOnly() throws Exception {
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    private JsonNode specification() throws Exception {
        var response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({OpenApiConfig.class, SecurityConfig.class, ApiExceptionHandler.class,
            RegistrationController.class, LoginController.class, UserController.class})
    static class TestApplication {
    }
}
