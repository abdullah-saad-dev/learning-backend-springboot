package com.example.demo.auth;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String prependedRole = user.getRole().toAuthority();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(prependedRole);
        return Collections.singleton(authority);
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // Without this the interface default returns true and the enabled column is inert, so a
    // deactivated account still authenticates. The column is NOT NULL, meaning null only reaches
    // here on an entity that was never persisted; comparing this way rather than unboxing treats
    // such a user as disabled instead of throwing inside the authentication chain.
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }

}
