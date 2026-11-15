package com.secureflow.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

record LoginResponse(
        @JsonProperty("access_token") @Schema(description = "Signed access JWT", example = "<access-token>")
        String accessToken,

        @JsonProperty("refresh_token") @Schema(description = "Opaque refresh credential", example = "<refresh-token>")
        String refreshToken,

        @JsonProperty("token_type") String tokenType,

        @JsonProperty("expires_in") @Schema(description = "Access token lifetime in seconds")
        long expiresIn) {
    @Override
    public String toString() {
        return "LoginResponse[tokens=REDACTED]";
    }
}
