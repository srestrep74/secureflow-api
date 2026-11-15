package com.secureflow.identity.domain;

import java.util.Locale;
import java.util.Objects;

public record Username(String value) {
    public Username {
        value = Objects.requireNonNull(value, "username").trim();
        if (!value.matches("[a-zA-Z0-9._-]{3,32}")) {
            throw new IllegalArgumentException("Invalid username");
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}
