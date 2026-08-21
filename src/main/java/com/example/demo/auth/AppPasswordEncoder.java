package com.example.demo.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class AppPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate;
    private final String defaultEncoder;

    public AppPasswordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        this.defaultEncoder = "bcrypt";
        this.delegate = new DelegatingPasswordEncoder(defaultEncoder, encoders);
    }

    @Override
    public @Nullable String encode(@Nullable CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(@Nullable String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
