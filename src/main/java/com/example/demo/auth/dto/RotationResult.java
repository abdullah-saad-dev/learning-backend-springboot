package com.example.demo.auth.dto;

import java.util.UUID;

public record RotationResult(String rawRefreshToken, UUID userId) {
}
