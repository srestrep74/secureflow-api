package com.secureflow.identity.application;

public interface AdministratorGuardPort {
    void lock();

    long countActiveAdministrators();
}
