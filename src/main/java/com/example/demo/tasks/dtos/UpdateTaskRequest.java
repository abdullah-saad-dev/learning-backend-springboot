package com.example.demo.tasks.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskRequest(@NotBlank String title, String details, @NotNull Boolean done,
                                @NotNull Long version) {
}
