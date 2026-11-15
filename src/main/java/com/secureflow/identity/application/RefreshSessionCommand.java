package com.secureflow.identity.application;

public record RefreshSessionCommand(String refreshToken) {
    @Override
    public String toString() {
        return "RefreshSessionCommand[REDACTED]";
    }
}
