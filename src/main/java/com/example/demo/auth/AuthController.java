package com.example.demo.auth;

import com.example.demo.auth.dto.JwtDetails;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.Tokens;
import com.example.demo.auth.entity.RefreshToken;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtDetails> login(@Valid @RequestBody LoginRequest request) {
        Tokens tokens =  authService.login(request.email(), request.password());
        return setCookieAndGetResponse(tokens);
    }
    @PostMapping("/refresh")
    public ResponseEntity<JwtDetails> refresh(@CookieValue("refreshToken") String refreshToken) {
        Tokens tokens = authService.refresh(refreshToken);
        return setCookieAndGetResponse(tokens);
    }
    @PostMapping("/signup")
    public ResponseEntity<Void> Singup(@RequestBody SignupRequest request) {
        authService.singup(request.email(), request.password(),request.username());
        return ResponseEntity.ok().build();
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return
                ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();
    }
    private ResponseEntity<JwtDetails> setCookieAndGetResponse(Tokens tokens) {
        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(tokens.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(tokens.jwtDetails());
    }
}
