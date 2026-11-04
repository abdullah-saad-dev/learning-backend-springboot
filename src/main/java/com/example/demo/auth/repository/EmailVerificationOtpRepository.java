package com.example.demo.auth.repository;

import com.example.demo.auth.entity.EmailVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, UUID> {

    Optional<EmailVerificationOtp> findByEmail(String email);

    void deleteByEmail(String email);

    /**
     * Deletes all OTP records whose expiration timestamp is earlier than {@code now}.
     */
    @Modifying
    @Query("""
        delete from EmailVerificationOtp o
        where o.expiresAt < :now
        """)
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
