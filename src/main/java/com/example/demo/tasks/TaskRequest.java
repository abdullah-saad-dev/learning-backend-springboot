package com.example.demo.tasks;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(@NotBlank String title, String details, Boolean done) {

}
