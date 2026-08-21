package com.example.demo.auth.service;

import com.example.demo.auth.AppUserDetails;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public LoginResponse login(String email, String password){
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        AppUserDetails userDetails = (AppUserDetails) authenticationManager.authenticate(authenticationToken);
        String token = jwtService.generateToken(userDetails.getUser());
        return new LoginResponse(token, jwtService.getIssuedAt(token), jwtService.getExpirationTime(token));
    }

}
