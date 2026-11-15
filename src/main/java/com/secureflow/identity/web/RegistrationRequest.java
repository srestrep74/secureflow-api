package com.secureflow.identity.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Public registration details")
public record RegistrationRequest(
        @NotBlank @Size(max = 256) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "sample.user")
        String username,

        @NotBlank @Size(max = 512) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.test")
        String email,

        @NotBlank
        @Size(max = 256)
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "12–128 Unicode code points, at most 512 UTF-8 bytes; never normalized")
        String password) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown field");
    }

    @Override
    public String toString() {
        return "RegistrationRequest[password=REDACTED]";
    }
}
