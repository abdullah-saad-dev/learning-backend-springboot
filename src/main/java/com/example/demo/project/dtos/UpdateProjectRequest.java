package com.example.demo.project.dtos;

import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateProjectRequest(UUID id, String name, String description, ProjectPriority priority,
                                   ProjectStatus status, LocalDate startDate, LocalDate dueDate, Long version) {
}
