package com.secureflow.identity.application;

public final class LogoutUnavailableException extends RuntimeException {
    public LogoutUnavailableException(Throwable cause) {
        super("Logout unavailable", cause);
    }
}
