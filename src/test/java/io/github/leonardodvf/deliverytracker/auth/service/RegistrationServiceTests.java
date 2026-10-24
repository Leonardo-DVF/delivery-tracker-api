package io.github.leonardodvf.deliverytracker.auth.service;

import io.github.leonardodvf.deliverytracker.auth.dto.RegisterRequest;
import io.github.leonardodvf.deliverytracker.auth.exception.EmailAlreadyRegisteredException;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTests {

    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final RegistrationService registration = new RegistrationService(users, passwords);

    @BeforeEach
    void configurePersistence() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalizesEmailHashesPasswordAndAlwaysCreatesCustomer() {
        var request = new RegisterRequest(" Customer ", " CUSTOMER@Example.test ", "test-password-2026");

        var result = registration.register(request);

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).existsByEmail("customer@example.test");
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(request.password());
        assertThat(passwords.matches(request.password(), saved.getValue().getPasswordHash())).isTrue();
        assertThat(result.id()).isEqualTo(saved.getValue().getId());
        assertThat(result.name()).isEqualTo("Customer");
        assertThat(result.email()).isEqualTo("customer@example.test");
        assertThat(result.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void rejectsPreviouslyRegisteredEmailWithoutSaving() {
        when(users.existsByEmail("customer@example.test")).thenReturn(true);

        assertThatThrownBy(() -> registration.register(
                new RegisterRequest("Customer", "CUSTOMER@example.test", "test-password-2026")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void propagatesDatabaseConflictWhenAnotherRegistrationWinsTheRace() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> registration.register(
                new RegisterRequest("Customer", "customer@example.test", "test-password-2026")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
