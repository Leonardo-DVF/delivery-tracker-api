package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.auth.bootstrap.LocalOperatorBootstrap;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LocalOperatorIntegrationTests extends JwtTestSupport {

    private static final String EMAIL = "operator@example.test";
    private static final String PASSWORD = "local-operator-password-2026";

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private Validator validator;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void createsOperatorOnceAndAllowsLoginAndProfileLookup() throws Exception {
        var bootstrap = new LocalOperatorBootstrap(users, passwords, validator, " OPERATOR@EXAMPLE.TEST ", PASSWORD);
        bootstrap.run(new DefaultApplicationArguments());
        var operator = users.findByEmail(EMAIL).orElseThrow();
        var originalHash = operator.getPasswordHash();

        bootstrap.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(1);
        assertThat(operator.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(passwords.matches(PASSWORD, originalHash)).isTrue();
        assertThat(users.findByEmail(EMAIL).orElseThrow().getPasswordHash()).isEqualTo(originalHash);

        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var token = json.readTree(login.getContentAsString()).path("accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(operator.getId().toString()))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void keepsExistingCustomerWhenConfiguredOperatorEmailConflicts() {
        var originalHash = passwords.encode(PASSWORD);
        var customer = users.saveAndFlush(new User("Customer", EMAIL, originalHash, UserRole.CUSTOMER));
        var bootstrap = new LocalOperatorBootstrap(users, passwords, validator, EMAIL, PASSWORD);

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);

        var stored = users.findById(customer.getId()).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(stored.getPasswordHash()).isEqualTo(originalHash);
        assertThat(users.count()).isEqualTo(1);
    }
}
