package com.example.demo.notes;

import jakarta.validation.constraints.NotBlank;

public record NoteRequest(@NotBlank String title) {

}
