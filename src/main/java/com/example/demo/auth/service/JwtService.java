package com.example.demo.auth.service;

import com.example.demo.auth.dto.JwtDetails;
import com.example.demo.auth.enums.Role;
import com.example.demo.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    @Value("${app.jwt.ttl-minutes}")
    private int ttlMinutes;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
    }

    /**
     * Generates a signed HS256 access JWT encoding the user's UUID as {@code sub} and their system {@code role}.
     */
    public String generateToken(User user) {
        Instant now = clock.instant();

        Role role = user.getRole();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(getExpirationTime())
                .subject(user.getId().toString())
                .claim("role", role)
                .build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Computes the expiration timestamp for a newly minted access token based on the configured TTL.
     */
    private Instant getExpirationTime() {
        return clock.instant().plus(ttlMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Extracts the expiration timestamp from an encoded JWT string.
     */
    private Instant getExpirationTime(String token) {
        return jwtDecoder.decode(token).getExpiresAt();
    }

    /**
     * Extracts the issued-at timestamp from an encoded JWT string.
     */
    private Instant getIssuedAt(String token) {
        return jwtDecoder.decode(token).getIssuedAt();
    }

    /**
     * Builds the {@link JwtDetails} response payload containing the raw token and its timestamps.
     */
    public JwtDetails getJwtDetails(String token) {
        return new JwtDetails(token,
                getIssuedAt(token), getExpirationTime(token));
    }
}
