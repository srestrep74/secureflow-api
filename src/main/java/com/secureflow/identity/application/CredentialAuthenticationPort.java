package com.secureflow.identity.application;

public interface CredentialAuthenticationPort {
    AuthenticatedIdentity authenticate(String identifier, String password);
}
