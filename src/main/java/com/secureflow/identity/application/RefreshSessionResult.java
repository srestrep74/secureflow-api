package com.secureflow.identity.application;

public record RefreshSessionResult(String accessToken, String refreshToken) {
    public String tokenType() {
        return "Bearer";
    }

    public long expiresIn() {
        return LoginResult.ACCESS_TOKEN_LIFETIME_SECONDS;
    }

    @Override
    public String toString() {
        return "RefreshSessionResult[REDACTED]";
    }
}
