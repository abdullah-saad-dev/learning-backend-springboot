package com.example.demo.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Boxed and @NotNull for the same reason as TaskPatch: POST and PUT both write "done" outright,
// so an omitted one used to be silently coerced to false and quietly mark a task pending.
// Callers must now say which they mean.
public record TaskRequest(@NotBlank String title, String details, @NotNull Boolean done) {

}
