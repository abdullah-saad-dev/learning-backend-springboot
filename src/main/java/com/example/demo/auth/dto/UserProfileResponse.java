package com.example.demo.auth.dto;

import com.example.demo.auth.entity.User;
import com.example.demo.auth.enums.Role;

import java.util.UUID;

/**
 * Who is signed in. The access token carries only the user id and role, so this is the one
 * place a client gets the name and email it needs to render the account menu.
 */
public record UserProfileResponse(UUID id, String username, String email, Role role) {
    public static UserProfileResponse of(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
