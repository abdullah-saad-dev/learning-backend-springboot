package com.example.demo.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AppSecurityContext {

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        UUID userId = null;
        if (principal instanceof Jwt jwt) {
            userId = UUID.fromString(jwt.getSubject());
        } else if (principal instanceof AppUserDetails userDetails && userDetails.getUser() != null) {
            userId = userDetails.getUser().getId();
        } else if (authentication.getName() != null) {
            try {
                userId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }

        log.atDebug()
                .addKeyValue("userId", userId)
                .setMessage("User ID retrieved from security context:")
                .log();
        return userId;
    }
}
