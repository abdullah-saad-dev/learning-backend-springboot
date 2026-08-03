package com.example.demo.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A full replace (PUT). Separate from TaskRequest because creating and editing genuinely differ:
 * a new task has no version to state, an edit does.
 * <p>
 * "version" is the version the caller last read. Required rather than optional: an optional
 * concurrency check is one nobody sends, which leaves the lost update it exists to prevent.
 */
public record TaskUpdateRequest(@NotBlank String title, String details, @NotNull Boolean done,
                                @NotNull Long version) {

}
