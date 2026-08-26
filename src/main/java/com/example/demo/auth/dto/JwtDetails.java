package com.example.demo.auth.dto;

import java.time.Instant;

public record JwtDetails(String accessToken, Instant issuedAt, Instant expiresAt) {
}
