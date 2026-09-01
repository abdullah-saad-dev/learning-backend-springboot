package com.example.demo.auth.exceptions;

import lombok.Getter;

@Getter
public class DuplicateEmailsException extends RuntimeException {
    private String email;
    public DuplicateEmailsException(String message, String email) {
        super(message);
        this.email = email;
    }
}
