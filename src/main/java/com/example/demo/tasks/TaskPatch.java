package com.example.demo.tasks;

import jakarta.validation.constraints.NotNull;

// Boxed so an omitted "done" arrives as null and fails @NotNull; a primitive would
// silently default to false and mark the task pending.
// "version" is the version the caller last read - see TaskUpdateRequest.
public record TaskPatch(@NotNull Boolean done, @NotNull Long version) {

}
