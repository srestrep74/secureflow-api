package com.secureflow.identity.application;

public interface PasswordHashingPort {
    String hash(String password);
}
