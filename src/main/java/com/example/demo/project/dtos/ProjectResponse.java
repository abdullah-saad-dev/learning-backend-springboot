package com.example.demo.project.dtos;

import com.example.demo.project.Project;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String description, ProjectPriority priority,
                              ProjectStatus status, Long version, Instant createdAt) {
    public static ProjectResponse of(Project project) {
        return new ProjectResponse(project.getId(), project.getName(),
                project.getDescription(),
                project.getPriority(),
                project.getStatus(),
                project.getVersion(),
                project.getCreatedAt());
    }

    public static List<ProjectResponse> of(List<Project> projects) {
        return projects
                .stream()
                .map(ProjectResponse::of)
                .toList();
    }

}

