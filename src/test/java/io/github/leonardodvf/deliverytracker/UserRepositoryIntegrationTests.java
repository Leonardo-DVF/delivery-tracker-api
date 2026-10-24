package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryIntegrationTests extends JwtTestSupport {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode("test-password");

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsCustomerAndFindsNormalizedEmail() {
        var before = Instant.now();
        var user = users.saveAndFlush(new User(" Customer ", " Customer@Example.test ",
                PASSWORD_HASH, UserRole.CUSTOMER));
        var id = user.getId();
        entityManager.clear();

        var stored = users.findByEmail("customer@example.test").orElseThrow();

        assertThat(stored.getId()).isEqualTo(id);
        assertThat(stored.getId().version()).isEqualTo(4);
        assertThat(stored.getName()).isEqualTo("Customer");
        assertThat(stored.getEmail()).isEqualTo("customer@example.test");
        assertThat(stored.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(stored.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(stored.getCreatedAt()).isCloseTo(before, within(1, ChronoUnit.SECONDS));
        assertThat(users.existsByEmail("customer@example.test")).isTrue();
        assertThat(users.findByEmail("unknown@example.test")).isEmpty();
    }

    @Test
    void persistsOperatorRoleAsText() {
        var user = users.saveAndFlush(new User("Operator", "operator@example.test",
                PASSWORD_HASH, UserRole.OPERATOR));
        entityManager.clear();

        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id = ?", String.class, user.getId()))
                .isEqualTo("OPERATOR");
    }

    @Test
    void rejectsDuplicateEmailsAfterNormalization() {
        users.saveAndFlush(new User("First", "customer@example.test", PASSWORD_HASH, UserRole.CUSTOMER));

        assertThatThrownBy(() -> users.saveAndFlush(
                new User("Second", " CUSTOMER@EXAMPLE.TEST ", PASSWORD_HASH, UserRole.CUSTOMER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_app_users_email");
    }

    @Test
    void databaseRejectsEmailThatBypassesNormalization() {
        assertThatThrownBy(() -> insertDirectly("Customer@Example.test", "CUSTOMER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_users_email_normalized");
    }

    @Test
    void databaseRejectsUnknownRoles() {
        assertThatThrownBy(() -> insertDirectly("customer@example.test", "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_users_role");
    }

    private void insertDirectly(String email, String role) {
        jdbc.update("""
                INSERT INTO app_users (id, name, email, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), "Customer", email, PASSWORD_HASH, role);
    }
}
