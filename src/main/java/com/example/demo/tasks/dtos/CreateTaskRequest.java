package com.example.demo.tasks.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

// "done" is boxed and @NotNull because a create writes it outright: an omitted one used to be
// silently coerced to false and quietly mark a task pending. Callers must say which they mean.
// dueDate and assigneeId are genuinely optional: null means no deadline / unassigned.
public record CreateTaskRequest(@NotNull UUID projectId, @NotBlank String title,
                                String details, @NotNull Boolean done,
                                LocalDate dueDate, UUID assigneeId) {

}
