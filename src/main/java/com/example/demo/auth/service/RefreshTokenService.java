package com.example.demo.auth.service;

import com.example.demo.auth.exceptions.InvalidRefreshTokenException;
import com.example.demo.auth.dto.RotationResult;
import com.example.demo.auth.entity.RefreshToken;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.enums.RefreshTokenStatus;
import com.example.demo.auth.repository.RefreshTokenRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    private final Clock clock;

    private final SecureRandom secureRandom;

    @Value("${app.refreshToken.absolute-expiration-days}")
    private int absoluteExpirationDays;
    @Value("${app.refreshToken.ttl-days}")
    private int refreshTokenTtlDays;
    @Value("${app.refreshToken.rotation-grace-seconds}")
    private int refreshTokenRotationGraceSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }


    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public RotationResult refresh(String rawToken) {
        Instant now = clock.instant();
        String hashToken = hashToken(rawToken);
        int rotatedTokens = refreshTokenRepository.markRotated(hashToken, now);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken)
                .orElseThrow(() -> {
                    log.debug("token not found");
                    return new InvalidRefreshTokenException("Token not found");
                });
        MDC.put("userId", token.getUserId().toString());
        if (rotatedTokens == 0) {
            diagnoseToken(token, now);
        }
        RefreshToken newToken = refreshTokenRepository.save(generateNewToken(token, now));
        return new RotationResult(newToken.getTokenString(), newToken.getUserId());
    }

    @Transactional
    public String mintToken(User user) {
        String token = generateToken();
        Instant now = clock.instant();
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .tokenHash(hashToken(token))
                        .status(RefreshTokenStatus.ACTIVE)
                        .absoluteExpiresAt(now.plus(absoluteExpirationDays, ChronoUnit.DAYS))
                        .expiresAt(now.plus(refreshTokenTtlDays, ChronoUnit.DAYS))
                        .tokenString(token)
                        .issuedAt(now)
                        .userId(user.getId())
                        .familyId(UuidCreator.getTimeOrderedEpoch(now))
                        .build()
        );
        return token;
    }

    /**
     * Ends the session behind this cookie: the whole family, not just the presented token,
     * so a copy of an older rotation cannot be replayed afterwards. Idempotent - an unknown,
     * expired or already-revoked token is not an error, because the caller's goal (this
     * device signed out) already holds.
     */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(rawToken)).ifPresent(token -> {
            int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId());
            log.atInfo()
                    .setMessage("user logged out")
                    .addKeyValue("userId", token.getUserId())
                    .addKeyValue("familyId", token.getFamilyId())
                    .addKeyValue("revokedTokens", revoked)
                    .log();
        });
    }

    /** Ends every session the user has, on every device. */
    @Transactional
    public void logoutAll(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId);
        log.atInfo()
                .setMessage("user logged out everywhere")
                .addKeyValue("userId", userId)
                .addKeyValue("revokedTokens", revoked)
                .log();
    }

    private RefreshToken generateNewToken(RefreshToken oldToken, Instant now) {
        String newTokenString = generateToken();
        String newTokenHash = hashToken(newTokenString);
        return RefreshToken.builder()
                .tokenHash(newTokenHash)
                .issuedAt(now)
                .expiresAt(now.plus(refreshTokenTtlDays, ChronoUnit.DAYS))
                .absoluteExpiresAt(oldToken.getAbsoluteExpiresAt())
                .familyId(oldToken.getFamilyId())
                .userId(oldToken.getUserId())
                .status(RefreshTokenStatus.ACTIVE)
                .tokenString(newTokenString)
                .build();
    }

    private void diagnoseToken(RefreshToken token, Instant now) {
        if (token.getExpiresAt().isBefore(now)
                || token.getAbsoluteExpiresAt().isBefore(now)) {
            log.atDebug()
                    .setMessage("token expired")
                    .addKeyValue("familyId", token.getFamilyId())
                    .log();
            throw new InvalidRefreshTokenException("expired");
        } else if (token.getStatus() == RefreshTokenStatus.ROTATED
                && token.getRotatedAt().isAfter(now.minusSeconds(refreshTokenRotationGraceSeconds))) {
            log.atDebug()
                    .setMessage("concurrent refresh")
                    .addKeyValue("familyId", token.getFamilyId())
                    .addKeyValue("rotatedAgoSeconds", Duration.between(token.getRotatedAt(), now).toSeconds())
                    .log();
            throw new InvalidRefreshTokenException("benign concurrent refresh");
        } else if (token.getStatus() == RefreshTokenStatus.ROTATED
                && token.getRotatedAt().isBefore(now.minusSeconds(refreshTokenRotationGraceSeconds))) {
            refreshTokenRepository.revokeFamily(token.getFamilyId());
            log.atWarn()
                    .setMessage("reuse detected")
                    .addKeyValue("familyId", token.getFamilyId())
                    .addKeyValue("rotatedAgoSeconds", Duration.between(token.getRotatedAt(), now).toSeconds())
                    .log();
            throw new InvalidRefreshTokenException("token reuse detected");
        } else {
            log.atDebug()
                    .setMessage("token revoked")
                    .addKeyValue("familyId", token.getFamilyId())
                    .log();
            throw new InvalidRefreshTokenException("token revoked");
        }
    }
}
