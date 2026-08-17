package com.example.demo.tasks.dtos;

import jakarta.validation.constraints.NotNull;

public record SetDoneRequest(
        @NotNull Long version,
        @NotNull Boolean done
){}
