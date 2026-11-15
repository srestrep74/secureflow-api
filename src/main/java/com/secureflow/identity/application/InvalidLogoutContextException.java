package com.secureflow.identity.application;

public final class InvalidLogoutContextException extends RuntimeException {
    public InvalidLogoutContextException() {
        super("Invalid logout context");
    }
}
