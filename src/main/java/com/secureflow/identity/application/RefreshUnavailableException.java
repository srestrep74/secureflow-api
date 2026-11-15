package com.secureflow.identity.application;

public final class RefreshUnavailableException extends RuntimeException {
    public RefreshUnavailableException() {
        super("Refresh unavailable");
    }
}
