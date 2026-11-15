package com.secureflow.identity.application;

public record LoginCommand(String identifier, String password) {
    @Override
    public String toString() {
        return "LoginCommand[credentials=REDACTED]";
    }
}
