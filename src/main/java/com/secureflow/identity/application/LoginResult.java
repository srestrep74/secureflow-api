package com.secureflow.identity.application;

public record LoginResult(String accessToken, String refreshToken) {
    @Override
    public String toString() {
        return "LoginResult[tokens=REDACTED]";
    }
}
