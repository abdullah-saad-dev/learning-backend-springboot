package com.example.demo.tasks.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// "done" is boxed and @NotNull because a create writes it outright: an omitted one used to be
// silently coerced to false and quietly mark a task pending. Callers must say which they mean.
public record CreateTaskRequest(@NotBlank String title, String details, @NotNull Boolean done) {

}
