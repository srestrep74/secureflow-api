package com.secureflow.identity.application;

public record AuthenticateCredentialsCommand(String identifier, String password) {
    @Override
    public String toString() {
        return "AuthenticateCredentialsCommand[credentials=REDACTED]";
    }
}
