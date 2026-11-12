package edu.oop.itsm.service;

import edu.oop.itsm.domain.Administrator;
import edu.oop.itsm.domain.Role;
import edu.oop.itsm.domain.User;
import edu.oop.itsm.exception.DuplicateResourceException;
import edu.oop.itsm.exception.InvalidCredentialsException;
import edu.oop.itsm.exception.NotFoundException;
import edu.oop.itsm.repository.UserRepository;
import edu.oop.itsm.security.TokenService;
import edu.oop.itsm.web.dto.AuthDtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(10);

    private final UserRepository users;
    private final UserFactory factory;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;
    // brute-force guard. ponytail: per-node memory; move to Redis when load-balanced
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, UserFactory factory, PasswordEncoder encoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.factory = factory;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        if (recentFailures(email) >= MAX_FAILURES)
            throw new InvalidCredentialsException("Too many failed attempts. Try again in a few minutes.");
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        // same message whether the email or the password is wrong, so accounts cannot be enumerated
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            failures.computeIfAbsent(email, k -> new ConcurrentLinkedDeque<>()).add(clock.instant());
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (!u.isActive()) throw new InvalidCredentialsException("This account has been deactivated");
        failures.remove(email);
        return new TokenResponse(tokens.issue(u), tokens.ttl().toSeconds(), profile(u));
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) throw new DuplicateResourceException("An account with this email already exists");
        User u = factory.create(Role.EMPLOYEE, req.fullName().trim(), req.email(), req.password());
        u.setDepartment(req.department());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone());
        users.save(u);
        return new TokenResponse(tokens.issue(u), tokens.ttl().toSeconds(), profile(u));
    }

    @Transactional(readOnly = true)
    public Profile me(Long userId) {
        return profile(users.findById(userId).orElseThrow(() -> new NotFoundException("User not found")));
    }

    public static Profile profile(User u) {
        return new Profile(u.getId(), u.getCode(), u.getFullName(), u.getEmail(), u.getRole(), u.getDepartment(), u.getPhone(),
                u instanceof Administrator a && a.isItManager());
    }

    private int recentFailures(String email) {
        Deque<Instant> q = failures.get(email);
        if (q == null) return 0;
        Instant cutoff = clock.instant().minus(LOCK_WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
        return q.size();
    }
}
