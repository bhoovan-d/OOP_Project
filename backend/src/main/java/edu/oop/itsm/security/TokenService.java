package edu.oop.itsm.security;

import edu.oop.itsm.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${itsm.jwt.ttl:PT8H}") Duration ttl) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("i-itsm")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("name", user.getFullName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Duration ttl() { return ttl; }
}
