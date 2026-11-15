package com.secureflow.identity.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

record RefreshRequest(
        @JsonProperty("refresh_token")
        @JsonDeserialize(using = StrictTokenDeserializer.class)
        @NotBlank
        @Size(max = 256)
        @Schema(description = "Single-use opaque refresh credential", example = "<refresh-token>")
        String refreshToken) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown field");
    }

    @Override
    public String toString() {
        return "RefreshRequest[token=REDACTED]";
    }

    static final class StrictTokenDeserializer extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }
}
