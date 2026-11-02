package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 30, message = "Display name must be at most 30 characters") String username,
        @Size(max = 500, message = "Bio must be at most 500 characters") String bio,
        @Size(max = 1_500_000, message = "Avatar image is too large") String avatarUrl
) {
}
