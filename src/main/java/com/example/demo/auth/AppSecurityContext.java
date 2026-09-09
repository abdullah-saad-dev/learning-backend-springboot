package com.example.demo.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AppSecurityContext {

    public UUID getCurrentUserId() {
        log.trace("Getting user ID Jwt using SecurityContextHolder");
        Jwt principle = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID userId = UUID.fromString(principle.getSubject());
        log.atInfo()
                .addKeyValue("userId", userId)
                .setMessage("User ID retrieved from security context:")
                .log();
        return userId;
    }
}
