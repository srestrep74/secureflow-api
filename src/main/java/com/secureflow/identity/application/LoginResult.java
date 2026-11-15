package com.secureflow.identity.application;

public record LoginResult(String accessToken, String refreshToken) {
    public static final long ACCESS_TOKEN_LIFETIME_SECONDS = 300;

    @Override
    public String toString() {
        return "LoginResult[tokens=REDACTED]";
    }
}
