package com.example.demo.tasks.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

// PUT replaces the whole editable state, so an omitted dueDate or assigneeId clears it -
// the client sends back what it read and changes only what the user changed.
public record UpdateTaskRequest(@NotBlank String title, String details, @NotNull Boolean done,
                                LocalDate dueDate, UUID assigneeId,
                                @NotNull Long version) {
}
