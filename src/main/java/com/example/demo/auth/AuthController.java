package com.example.demo.auth;

import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RequestMapping("/auth")
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
            return authService.login(request.email(), request.password());
    }
}
