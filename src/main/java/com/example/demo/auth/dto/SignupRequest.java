package com.example.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Pattern(
                regexp = "^(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "Password must include at least 1 number and 1 special character"
        )
        String password,
        String otp
) {
    public SignupRequest(String username, String email, String password) {
        this(username, email, password, null);
    }
}
