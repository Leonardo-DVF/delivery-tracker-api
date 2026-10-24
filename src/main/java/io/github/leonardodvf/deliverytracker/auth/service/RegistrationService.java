package io.github.leonardodvf.deliverytracker.auth.service;

import io.github.leonardodvf.deliverytracker.auth.dto.RegisterRequest;
import io.github.leonardodvf.deliverytracker.auth.exception.EmailAlreadyRegisteredException;
import io.github.leonardodvf.deliverytracker.user.dto.UserResponse;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }
        var user = new User(request.name(), request.email(), passwords.encode(request.password()), UserRole.CUSTOMER);
        return UserResponse.from(users.saveAndFlush(user));
    }
}
