package com.example.demo.auth;

public enum Role {
    USER,
    ADMIN;

    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
