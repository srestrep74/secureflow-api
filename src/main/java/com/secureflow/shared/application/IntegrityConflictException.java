package com.secureflow.shared.application;

public final class IntegrityConflictException extends RuntimeException {
    public IntegrityConflictException(String message, Throwable cause) { super(message, cause); }
}
