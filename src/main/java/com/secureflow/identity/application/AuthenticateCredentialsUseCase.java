package com.secureflow.identity.application;

import java.nio.charset.StandardCharsets;

public final class AuthenticateCredentialsUseCase {
    private final CredentialAuthenticationPort authentication;

    public AuthenticateCredentialsUseCase(CredentialAuthenticationPort authentication) {
        this.authentication = authentication;
    }

    public AuthenticatedIdentity authenticate(AuthenticateCredentialsCommand command) {
        if (command == null
                || command.identifier() == null
                || command.identifier().length() > 256
                || command.password() == null
                || command.password().length() > 256
                || command.password().isBlank()
                || command.password().getBytes(StandardCharsets.UTF_8).length > 512
                || !StandardCharsets.UTF_8.newEncoder().canEncode(command.password())) {
            throw new InvalidCredentialsException();
        }
        return authentication.authenticate(command.identifier(), command.password());
    }
}
