package com.example.demo.auth.enums;

public enum Role {
    USER,
    ADMIN;

    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
