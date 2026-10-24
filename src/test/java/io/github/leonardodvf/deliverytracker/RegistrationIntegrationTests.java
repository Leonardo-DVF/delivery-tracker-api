package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RegistrationIntegrationTests extends JwtTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void registersCustomerAndPersistsOnlyPasswordHash() throws Exception {
        var request = Map.of("name", " Customer ", "email", " CUSTOMER@Example.test ",
                "password", "integration-password-2026");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Customer"))
                .andExpect(jsonPath("$.email").value("customer@example.test"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var saved = users.findByEmail("customer@example.test").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getPasswordHash()).isNotEqualTo(request.get("password"));
        assertThat(passwords.matches(request.get("password"), saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailIgnoringCaseAndSurroundingWhitespace() throws Exception {
        var request = Map.of("name", "Customer", "email", "customer@example.test",
                "password", "integration-password-2026");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isCreated());

        var duplicate = Map.of("name", "Other", "email", " CUSTOMER@EXAMPLE.TEST ",
                "password", "integration-password-2026");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void rejectsOperatorRoleInPublicPayloadWithoutPersistingUser() throws Exception {
        var request = Map.of("name", "Customer", "email", "customer@example.test",
                "password", "integration-password-2026", "role", "OPERATOR");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(users.count()).isZero();
    }
}
