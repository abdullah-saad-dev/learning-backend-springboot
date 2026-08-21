package com.example.demo.auth.dto;

import java.time.Instant;

public record LoginResponse(String accessToken,Instant issuedAt, Instant expiresAt) {
}
