package com.secureflow.identity.web;

import com.secureflow.identity.application.LoginCommand;
import com.secureflow.identity.application.LoginResult;

final class LoginMapper {
    LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.identifier(), request.password());
    }

    LoginResponse toResponse(LoginResult result) {
        return new LoginResponse(
                result.accessToken(), result.refreshToken(), "Bearer", LoginResult.ACCESS_TOKEN_LIFETIME_SECONDS);
    }
}
