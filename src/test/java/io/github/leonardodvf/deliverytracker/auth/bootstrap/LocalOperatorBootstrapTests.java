package io.github.leonardodvf.deliverytracker.auth.bootstrap;

import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalOperatorBootstrapTests {

    private static final String EMAIL = "operator@example.test";
    private static final String PASSWORD = "local-operator-password-2026";
    private static ValidatorFactory validation;
    private static Validator validator;

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = spy(new BCryptPasswordEncoder(4));

    @BeforeAll
    static void prepareValidation() {
        validation = Validation.buildDefaultValidatorFactory();
        validator = validation.getValidator();
    }

    @AfterAll
    static void closeValidation() {
        validation.close();
    }

    @Test
    void doesNothingWhenCredentialsAreNotConfigured() {
        bootstrap("", "").run(new DefaultApplicationArguments());

        verifyNoInteractions(users, passwords);
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void createsOperatorWithNormalizedEmailAndBcryptHash(String password) {
        bootstrap(" OPERATOR@Example.test ", password).run(new DefaultApplicationArguments());

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).findByEmail(EMAIL);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(saved.getValue().getName()).isEqualTo("Local operator");
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(password);
        assertThat(passwords.matches(password, saved.getValue().getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void rejectsIncompleteOrInvalidConfigurationWithoutAccessingDatabase(String email, String password) {
        assertThatThrownBy(() -> bootstrap(email, password).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configure both LOCAL_OPERATOR_EMAIL and LOCAL_OPERATOR_PASSWORD");

        verifyNoInteractions(users, passwords);
    }

    @Test
    void keepsExistingOperatorAndPasswordUnchanged() {
        var existing = new User("Existing operator", EMAIL, "existing-hash", UserRole.OPERATOR);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        bootstrap(EMAIL, PASSWORD).run(new DefaultApplicationArguments());
        bootstrap(EMAIL, PASSWORD).run(new DefaultApplicationArguments());

        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(existing.getName()).isEqualTo("Existing operator");
        verify(users, times(2)).findByEmail(EMAIL);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void refusesToPromoteExistingCustomerAndDoesNotExposeCredentialsInError() {
        var existing = new User("Customer", EMAIL, "existing-hash", UserRole.CUSTOMER);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> bootstrap(EMAIL, PASSWORD).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The configured local operator email belongs to a customer")
                .hasMessageNotContaining(PASSWORD)
                .hasMessageNotContaining(EMAIL);

        assertThat(existing.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod"})
    void doesNotRegisterBootstrapOutsideLocalProfile(String profile) {
        context(profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LocalOperatorBootstrap.class);
        });

        verifyNoInteractions(users, passwords);
    }

    @Test
    void registersBootstrapAndUsesConfiguredCredentialsOnlyInLocalProfile() {
        context("local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LocalOperatorBootstrap.class);
            context.getBean(LocalOperatorBootstrap.class).run(new DefaultApplicationArguments());
        });

        verify(users).findByEmail(EMAIL);
        verify(users).saveAndFlush(any(User.class));
    }

    private LocalOperatorBootstrap bootstrap(String email, String password) {
        return new LocalOperatorBootstrap(users, passwords, validator, email, password);
    }

    private ApplicationContextRunner context(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(LocalOperatorBootstrap.class)
                .withBean(UserRepository.class, () -> users)
                .withBean(PasswordEncoder.class, () -> passwords)
                .withBean(Validator.class, () -> validator)
                .withPropertyValues("LOCAL_OPERATOR_EMAIL=" + EMAIL, "LOCAL_OPERATOR_PASSWORD=" + PASSWORD);
    }

    static Stream<String> validPasswords() {
        return Stream.of(PASSWORD, "a".repeat(12), "é".repeat(36));
    }

    static Stream<Arguments> invalidCredentials() {
        return Stream.of(
                Arguments.of("", PASSWORD),
                Arguments.of(EMAIL, ""),
                Arguments.of(" ", PASSWORD),
                Arguments.of("invalid-email", PASSWORD),
                Arguments.of("a".repeat(255) + "@example.test", PASSWORD),
                Arguments.of(EMAIL, "short"),
                Arguments.of(EMAIL, " ".repeat(12)),
                Arguments.of(EMAIL, "a".repeat(73)),
                Arguments.of(EMAIL, "é".repeat(37)));
    }
}
