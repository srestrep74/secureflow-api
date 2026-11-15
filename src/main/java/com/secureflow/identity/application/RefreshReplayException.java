package com.secureflow.identity.application;

public final class RefreshReplayException extends RuntimeException {
    public RefreshReplayException() {
        super("Refresh token replay detected");
    }
}
