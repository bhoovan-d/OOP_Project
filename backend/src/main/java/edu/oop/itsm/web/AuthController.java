package edu.oop.itsm.web;

import edu.oop.itsm.event.Actor;
import edu.oop.itsm.service.AuthService;
import edu.oop.itsm.web.dto.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) { return auth.login(req); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) { return auth.register(req); }

    @GetMapping("/me")
    public Profile me(Actor actor) { return auth.me(actor.id()); }
}
