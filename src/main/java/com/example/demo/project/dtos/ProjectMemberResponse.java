package com.example.demo.project.dtos;

import com.example.demo.project.enums.ProjectRole;

import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        String username,
        String email,
        ProjectRole role
) {
}
