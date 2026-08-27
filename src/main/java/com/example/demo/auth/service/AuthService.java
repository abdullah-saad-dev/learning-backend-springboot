package com.example.demo.auth.service;

import com.example.demo.auth.AppUserDetails;
import com.example.demo.auth.dto.Tokens;
import com.example.demo.auth.dto.RotationResult;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public Tokens login(String email, String password) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        String accessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.mintToken(user);
        return new Tokens(jwtService.getJwtDetails(accessToken), newRefreshToken);
    }
    public Tokens refresh(String rawToken){
        RotationResult rotationResult = refreshTokenService.refresh(rawToken);
        String refreshToken = rotationResult.rawRefreshToken();
        User user = userRepository.findById(rotationResult.userId())
                .orElseThrow(() -> new UsernameNotFoundException("the owner of the token doesn't exist anymore"));
        String accessToken = jwtService.generateToken(user);
        return new Tokens(jwtService.getJwtDetails(accessToken), refreshToken);
    }
    @Transactional
    public void singup(String email, String password, String username) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .username(username)
                .enabled(true)
                .createdAt(java.time.Instant.now())
                .build();
        userRepository.save(user);
    }
}
