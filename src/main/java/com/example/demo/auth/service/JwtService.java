package com.example.demo.auth.service;

import com.example.demo.auth.Role;
import com.example.demo.auth.User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();

        Role role = user.getRole();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(getExpirationTime())
                .subject(user.getId().toString())
                .claim("role", role)
                .build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
    public Instant getExpirationTime(){
        return Instant.now().plus(15, ChronoUnit.MINUTES);
    }
    public Instant getExpirationTime(String token){
        return jwtDecoder.decode(token).getExpiresAt();
    }
    public Instant getIssuedAt(String token){
        return jwtDecoder.decode(token).getIssuedAt();
    }

}
