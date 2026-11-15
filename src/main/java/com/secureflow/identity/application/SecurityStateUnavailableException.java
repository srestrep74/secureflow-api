package com.secureflow.identity.application;

public final class SecurityStateUnavailableException extends RuntimeException {
    public SecurityStateUnavailableException() {
        super("Security state unavailable");
    }
}
