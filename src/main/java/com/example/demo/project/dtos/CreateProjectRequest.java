package com.example.demo.project.dtos;

import com.example.demo.auth.entity.User;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateProjectRequest(@NotBlank String name, String description, @NotNull ProjectPriority priority,
                                   @NotNull ProjectStatus status, LocalDate startDate, LocalDate dueDate,
                                   List<User> members) {
}
