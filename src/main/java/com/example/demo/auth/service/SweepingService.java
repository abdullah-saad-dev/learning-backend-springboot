package com.example.demo.auth.service;

import com.example.demo.auth.repository.EmailVerificationOtpRepository;
import com.example.demo.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Daily background maintenance service that purges expired credentials (refresh tokens and
 * unconsumed email verification OTPs) from PostgreSQL.
 */
@Slf4j
@Service
public class SweepingService {

    private static final long SWEEP_ADVISORY_LOCK_KEY = 424242L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SweepingService(RefreshTokenRepository refreshTokenRepository,
                           EmailVerificationOtpRepository otpRepository,
                           JdbcTemplate jdbcTemplate,
                           Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpRepository = otpRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Nightly scheduled job (03:00 daily) that purges refresh tokens past their absolute expiration
     * timestamp and unconsumed email verification OTPs past their TTL.
     * <p>
     * Uses a PostgreSQL transaction-scoped advisory lock ({@code pg_try_advisory_xact_lock}) so only
     * one application replica executes the cleanup when scaled horizontally.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void sweep() {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, SWEEP_ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("skipping expired credential sweep; another replica holds the advisory lock");
            return;
        }
        Instant now = clock.instant();
        int deletedTokens = refreshTokenRepository.deleteByAbsoluteExpiresAtBefore(now);
        int deletedOtps = otpRepository.deleteByExpiresAtBefore(now);
        log.atInfo()
                .setMessage("swept expired refresh tokens and unconsumed otps")
                .addKeyValue("deletedRefreshTokens", deletedTokens)
                .addKeyValue("deletedOtps", deletedOtps)
                .log();
    }
}
