package com.secureflow.identity.application;

public interface RegistrationTransactionPort {
    void execute(Runnable operation);
}
