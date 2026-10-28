package com.example.demo.project.dtos;

import com.example.demo.project.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddProjectMemberRequest(
        UUID userId,
        String email,
        @NotNull ProjectRole role
) {
}
