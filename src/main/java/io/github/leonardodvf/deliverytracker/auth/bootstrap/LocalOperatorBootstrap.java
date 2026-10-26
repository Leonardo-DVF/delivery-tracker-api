package io.github.leonardodvf.deliverytracker.auth.bootstrap;

import io.github.leonardodvf.deliverytracker.auth.dto.RegisterRequest;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class LocalOperatorBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final String email;
    private final String password;

    public LocalOperatorBootstrap(UserRepository users, PasswordEncoder passwords, Validator validator,
            @Value("${LOCAL_OPERATOR_EMAIL:}") String email,
            @Value("${LOCAL_OPERATOR_PASSWORD:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.validator = validator;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isEmpty() && password.isEmpty()) {
            return;
        }
        var request = new RegisterRequest("Local operator", email, password);
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Configure both LOCAL_OPERATOR_EMAIL and LOCAL_OPERATOR_PASSWORD: "
                    + "a valid email and a password with 12 to 72 characters and at most 72 UTF-8 bytes");
        }
        var existing = users.findByEmail(request.email());
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.OPERATOR) {
                throw new IllegalStateException("The configured local operator email belongs to a customer");
            }
            return;
        }
        users.saveAndFlush(new User(request.name(), request.email(),
                passwords.encode(request.password()), UserRole.OPERATOR));
    }
}
