package com.example.demo.auth.service;

import com.example.demo.auth.entity.EmailVerificationOtp;
import com.example.demo.auth.exceptions.DuplicateEmailsException;
import com.example.demo.auth.exceptions.InvalidOtpException;
import com.example.demo.auth.repository.EmailVerificationOtpRepository;
import com.example.demo.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
public class MailVerificationService {

    private final EmailVerificationOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration otpTtl;
    private final String mailFrom;

    public MailVerificationService(
            EmailVerificationOtpRepository otpRepository,
            UserRepository userRepository,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${app.otp.ttl:5m}") Duration otpTtl,
            @Value("${app.mail.from:no-reply@managable.local}") String mailFrom) {
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.clock = clock;
        this.otpTtl = otpTtl;
        this.mailFrom = mailFrom;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a 6-digit OTP for a prospective signup email, persists its SHA-256 hash,
     * and dispatches the verification email over SMTP via {@link JavaMailSender}.
     */
    @Transactional
    public String sendOtp(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailsException("this email is already registered", email);
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String otpHash = hashOtp(otp);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(otpTtl);

        EmailVerificationOtp entity = otpRepository.findByEmail(email)
                .orElseGet(() -> EmailVerificationOtp.builder()
                        .email(email)
                        .build());

        entity.setOtpHash(otpHash);
        entity.setExpiresAt(expiresAt);
        entity.setVerified(false);
        entity.setCreatedAt(now);
        otpRepository.saveAndFlush(entity);

        deliverOtpEmail(email, otp, expiresAt);

        return otp;
    }

    public void deliverOtpEmail(String email, String otp, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Your Managable verification code");
        message.setText("""
                Your 6-digit verification code is: %s

                This code expires in %d minutes.
                If you did not request this code, you can safely ignore this email.
                """.formatted(otp, otpTtl.toMinutes()));
        mailSender.send(message);

        log.atInfo()
                .setMessage("verification otp email sent")
                .addKeyValue("email", email)
                .addKeyValue("expiresAt", expiresAt)
                .log();
    }

    @Transactional
    public void verifyOtp(String email, String otp) {
        Instant now = clock.instant();
        EmailVerificationOtp entity = otpRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired verification code"));

        if (!entity.getExpiresAt().isAfter(now)) {
            throw new InvalidOtpException("Verification code has expired. Please request a new code.");
        }

        String candidateHash = hashOtp(otp);
        boolean matches = MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                entity.getOtpHash().getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            throw new InvalidOtpException("Invalid verification code. Please check the 6-digit code and try again.");
        }

        entity.setVerified(true);
        otpRepository.saveAndFlush(entity);

        log.atInfo()
                .setMessage("email otp verified")
                .addKeyValue("email", email)
                .log();
    }

    /**
     * Verifies and consumes an OTP during signup when an OTP is supplied or a pending
     * verification record exists for the email.
     */
    @Transactional
    public void verifyAndConsumeOnSignup(String email, String otp) {
        var existingOpt = otpRepository.findByEmail(email);
        if (otp != null && !otp.isBlank()) {
            verifyOtp(email, otp);
            otpRepository.deleteByEmail(email);
            return;
        }
        if (existingOpt.isPresent()) {
            EmailVerificationOtp entity = existingOpt.get();
            if (!Boolean.TRUE.equals(entity.getVerified()) || !entity.getExpiresAt().isAfter(clock.instant())) {
                throw new InvalidOtpException("Please verify the 6-digit code sent to your email before completing signup.");
            }
            otpRepository.delete(entity);
        }
    }

    public static String hashOtp(String rawOtp) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawOtp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
