package com.example.demo.auth.service;

import com.example.demo.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
public class SweepingService {

    private static final long SWEEP_ADVISORY_LOCK_KEY = 424242L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SweepingService(RefreshTokenRepository refreshTokenRepository,
                           JdbcTemplate jdbcTemplate,
                           Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void sweep() {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, SWEEP_ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("skipping expired refresh token sweep; another replica holds the advisory lock");
            return;
        }
        int deletedRows = refreshTokenRepository.deleteByAbsoluteExpiresAtBefore(clock.instant());
        log.atInfo()
                .setMessage("swept expired refresh tokens")
                .addKeyValue("deletedRows", deletedRows)
                .log();
    }
}
