package com.example.demo.project.dtos;

import com.example.demo.project.Project;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectRole;
import com.example.demo.project.enums.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectPriority priority,
        ProjectStatus status,
        Long version,
        Instant createdAt,
        LocalDate startDate,
        LocalDate dueDate,
        ProjectRole role,
        int memberCount
) {
    public static ProjectResponse of(Project project, UUID currentUserId) {
        ProjectRole role = currentUserId != null ? project.getRoleForUser(currentUserId) : null;
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getPriority(),
                project.getStatus(),
                project.getVersion(),
                project.getCreatedAt(),
                project.getStartDate(),
                project.getDueDate(),
                role,
                project.getMemberCount()
        );
    }

    public static ProjectResponse of(Project project) {
        return of(project, null);
    }

    public static List<ProjectResponse> of(List<Project> projects, UUID currentUserId) {
        return projects
                .stream()
                .map(p -> of(p, currentUserId))
                .toList();
    }

    public static List<ProjectResponse> of(List<Project> projects) {
        return of(projects, null);
    }
}
