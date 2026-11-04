package com.example.demo.auth;

import com.example.demo.auth.dto.JwtDetails;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.SendOtpRequest;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.Tokens;
import com.example.demo.auth.dto.UpdateProfileRequest;
import com.example.demo.auth.dto.UserProfileResponse;
import com.example.demo.auth.dto.VerifyOtpRequest;
import com.example.demo.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

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
    @Value("${app.refreshToken.ttl}")
    private Duration refreshTokenTtl;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user by email and password, returning a short-lived access JWT in the body
     * and setting the rotating refresh token in an {@code HttpOnly} cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<JwtDetails> login(@Valid @RequestBody LoginRequest request) {
        Tokens tokens = authService.login(request.email(), request.password());
        return setCookieAndGetResponse(tokens);
    }

    /**
     * Rotates the refresh token from the {@code HttpOnly} cookie and issues a fresh access JWT.
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtDetails> refresh(@CookieValue(REFRESH_COOKIE) String refreshToken) {
        Tokens tokens = authService.refresh(refreshToken);
        return setCookieAndGetResponse(tokens);
    }

    /**
     * Generates and sends a 6-digit verification OTP to a prospective signup email address.
     */
    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@RequestBody @Valid SendOtpRequest request) {
        authService.sendSignupOtp(request.email());
        return ResponseEntity.ok().build();
    }

    /**
     * Verifies a 6-digit OTP sent to the user's email address prior to completing registration.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<Void> verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        authService.verifySignupOtp(request.email(), request.otp());
        return ResponseEntity.ok().build();
    }

    /**
     * Registers a new user account after validating password rules and consuming any pending OTP.
     */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody @Valid SignupRequest request) {
        authService.signup(request.email(), request.password(), request.username(), request.otp());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the profile details of the currently authenticated user.
     */
    @GetMapping("/me")
    public UserProfileResponse me() {
        return authService.getMe();
    }

    /**
     * Updates the display name, bio, and profile photo of the currently authenticated user.
     */
    @PutMapping("/me")
    public UserProfileResponse updateMe(@RequestBody @Valid UpdateProfileRequest request) {
        return authService.updateMe(request);
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
                refreshCookie(tokens.rawRefreshToken(), REFRESH_COOKIE_PATH, refreshTokenTtl.toSeconds());
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
