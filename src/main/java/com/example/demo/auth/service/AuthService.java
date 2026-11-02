package com.example.demo.auth.service;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.auth.AppUserDetails;
import com.example.demo.auth.dto.Tokens;
import com.example.demo.auth.dto.RotationResult;
import com.example.demo.auth.dto.UpdateProfileRequest;
import com.example.demo.auth.dto.UserProfileResponse;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.exceptions.DuplicateEmailsException;
import com.example.demo.auth.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppSecurityContext securityContext;
    private final MailVerificationService mailVerificationService;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder, Clock clock,
                       AppSecurityContext securityContext,
                       MailVerificationService mailVerificationService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.securityContext = securityContext;
        this.mailVerificationService = mailVerificationService;
    }

    public Tokens login(String email, String password) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.mintToken(user);
        log.atInfo()
                .setMessage("user logged in")
                .addKeyValue("userId", user.getId())
                .log();
        return new Tokens(jwtService.getJwtDetails(accessToken), refreshToken);
    }

    public Tokens refresh(String rawToken) {
        RotationResult rotationResult = refreshTokenService.refresh(rawToken);
        String refreshToken = rotationResult.rawRefreshToken();
        User user = userRepository.findById(rotationResult.userId())
                .orElseThrow(() -> {
                    log.atWarn()
                            .setMessage("refresh token owner no longer exists")
                            .addKeyValue("userId", rotationResult.userId())
                            .log();
                    return new UsernameNotFoundException("the owner of the token doesn't exist anymore");
                });
        log.atDebug()
                .setMessage("user refreshed")
                .addKeyValue("userId", user.getId())
                .log();
        String accessToken = jwtService.generateToken(user);
        return new Tokens(jwtService.getJwtDetails(accessToken), refreshToken);
    }

    @Transactional
    public String sendSignupOtp(String email) {
        return mailVerificationService.sendOtp(email);
    }

    @Transactional
    public void verifySignupOtp(String email, String otp) {
        mailVerificationService.verifyOtp(email, otp);
    }

    @Transactional
    public void signup(String email, String password, String username) {
        signup(email, password, username, null);
    }

    @Transactional
    public void signup(String email, String password, String username, String otp) {
        mailVerificationService.verifyAndConsumeOnSignup(email, otp);
        try {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .username(username)
                    .enabled(true)
                    .createdAt(clock.instant())
                    .build();
            //save and flush otherwise it will throw outside the try block after the commit,
            // thus we won't be able to wrap it
            userRepository.saveAndFlush(user);
            log.atInfo()
                    .setMessage("user signed up")
                    .addKeyValue("userId", user.getId())
                    .log();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailsException("this email is already registered", email);
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe() {
        UUID userId = securityContext.getCurrentUserId();
        // A valid token for a deleted user: the token outlives the row by up to its TTL.
        // Answered as 401 rather than 404, because the caller is effectively not signed in.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("the signed-in user no longer exists"));
        return UserProfileResponse.of(user);
    }

    @Transactional
    public UserProfileResponse updateMe(UpdateProfileRequest request) {
        UUID userId = securityContext.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("the signed-in user no longer exists"));
        user.setUsername(request.username().trim());
        user.setBio(request.bio() != null && !request.bio().isBlank() ? request.bio().trim() : null);
        user.setAvatarUrl(request.avatarUrl() != null && !request.avatarUrl().isBlank() ? request.avatarUrl().trim() : null);
        User saved = userRepository.saveAndFlush(user);
        log.atInfo()
                .setMessage("user profile updated")
                .addKeyValue("userId", saved.getId())
                .log();
        return UserProfileResponse.of(saved);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }

    public void logoutAll() {
        refreshTokenService.logoutAll(securityContext.getCurrentUserId());
    }
}
