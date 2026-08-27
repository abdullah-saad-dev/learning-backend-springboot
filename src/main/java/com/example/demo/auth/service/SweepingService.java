package com.example.demo.auth.service;

import com.example.demo.auth.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SweepingService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public SweepingService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void sweep() {
        refreshTokenRepository.deleteByAbsoluteExpiresAtBefore(clock.instant());
    }
}
