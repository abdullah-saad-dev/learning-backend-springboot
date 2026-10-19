package com.example.demo.auth;

import com.example.demo.auth.dto.JwtDetails;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.Tokens;
import com.example.demo.auth.dto.UserProfileResponse;
import com.example.demo.auth.service.AuthService;
import com.example.demo.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
public class AuthController {
    private static final String REFRESH_COOKIE = "refreshToken";
    // Scoped to /auth rather than /auth/refresh so that /auth/logout receives it too - logout
    // has to know which family to revoke. Still never sent to /api/**.
    private static final String REFRESH_COOKIE_PATH = "/auth";
    // Where the cookie used to live. A browser holding one there would send it to
    // /auth/refresh ahead of the new one (longer paths first); once rotated, that stale copy
    // would read as token reuse and revoke the family. Every response that sets or clears
    // the cookie also expires the old one. Safe to drop once refresh TTLs have elapsed.
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/auth/refresh";

    private final AuthService authService;
    @Value("${app.refreshToken.ttl-seconds}")
    private int refreshTokenTtlSeconds;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtDetails> login(@Valid @RequestBody LoginRequest request) {
        Tokens tokens =  authService.login(request.email(), request.password());
        return setCookieAndGetResponse(tokens);
    }
    @PostMapping("/refresh")
    public ResponseEntity<JwtDetails> refresh(@CookieValue(REFRESH_COOKIE) String refreshToken) {
        Tokens tokens = authService.refresh(refreshToken);
        return setCookieAndGetResponse(tokens);
    }
    @PostMapping("/signup")
    public ResponseEntity<Void> Signup(@RequestBody @Valid SignupRequest request) {
        authService.signup(request.email(), request.password(),request.username());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return authService.getMe();
    }

    /**
     * Signs out this device. Works off the refresh cookie alone, with no access token
     * required: someone whose access token has already expired must still be able to log out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return clearCookieResponse();
    }

    /** Signs out every device. Needs the access token, since it acts on the user, not a cookie. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll();
        return clearCookieResponse();
    }

    private ResponseCookie refreshCookie(String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie expiredLegacyCookie() {
        return refreshCookie("", LEGACY_REFRESH_COOKIE_PATH, 0);
    }

    private ResponseEntity<JwtDetails> setCookieAndGetResponse(Tokens tokens) {
        ResponseCookie refreshTokenCookie =
                refreshCookie(tokens.rawRefreshToken(), REFRESH_COOKIE_PATH, refreshTokenTtlSeconds);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, expiredLegacyCookie().toString())
                .body(tokens.jwtDetails());
    }

    // Max-Age=0 with the same name, path and attributes is what makes the browser drop it.
    private ResponseEntity<Void> clearCookieResponse() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", REFRESH_COOKIE_PATH, 0).toString())
                .header(HttpHeaders.SET_COOKIE, expiredLegacyCookie().toString())
                .build();
    }
}
