package com.secureflow.identity.application;

public record RegisterUserCommand(String username, String email, String password) {
    @Override
    public String toString() {
        return "RegisterUserCommand[password=REDACTED]";
    }
}
