package edu.oop.itsm.service;

import edu.oop.itsm.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Factory pattern: one place decides which User subclass to build and always hashes the password. */
@Component
public class UserFactory {

    private final PasswordEncoder encoder;

    public UserFactory(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public User create(Role role, String fullName, String email, String rawPassword) {
        String hash = encoder.encode(rawPassword);
        return switch (role) {
            case EMPLOYEE -> new Employee(fullName, email, hash);
            case TECHNICIAN -> new Technician(fullName, email, hash);
            case ADMIN -> new Administrator(fullName, email, hash, false);
        };
    }
}
