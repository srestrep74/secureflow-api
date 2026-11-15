package com.secureflow.identity.web;

import com.secureflow.identity.application.RegisterUserCommand;
import com.secureflow.identity.application.RegistrationResult;

final class RegistrationMapper {
    RegisterUserCommand toCommand(RegistrationRequest request) {
        return new RegisterUserCommand(request.username(), request.email(), request.password());
    }

    RegistrationResponse toResponse(RegistrationResult result) {
        return new RegistrationResponse(result.userId().value(), result.username(), result.email());
    }
}
