package com.example.demo.auth.dto;

public record Tokens(JwtDetails jwtDetails, String rawRefreshToken) {
}
