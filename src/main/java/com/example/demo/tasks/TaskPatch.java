package com.example.demo.tasks;

import jakarta.validation.constraints.NotNull;

// Boxed so an omitted "done" arrives as null and fails @NotNull; a primitive would
// silently default to false and mark the task pending.
public record TaskPatch(@NotNull Boolean done) {

}
