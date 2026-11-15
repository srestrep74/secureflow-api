package com.secureflow.identity.application;

public final class AuthenticationUnavailableException extends RuntimeException {
    public AuthenticationUnavailableException() {
        super("Authentication unavailable");
    }
}
