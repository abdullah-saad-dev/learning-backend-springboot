package com.example.demo.project.dtos;

import com.example.demo.project.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(
        @NotNull ProjectRole role
) {
}
