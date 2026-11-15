package com.secureflow.identity.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank @Size(max = 256) @Schema(description = "Username or email", example = "sample.user")
        String identifier,

        @NotBlank
        @Size(max = 256)
        @Schema(description = "Password, preserved exactly", format = "password", example = "<password>")
        String password) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown field");
    }

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
