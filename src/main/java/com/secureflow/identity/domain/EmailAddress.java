package com.secureflow.identity.domain;

import java.util.Locale;
import java.util.Objects;

public record EmailAddress(String value) {
    public EmailAddress {
        value = Objects.requireNonNull(value, "email").trim();
        if (value.length() < 3 || value.length() > 254 || !value.matches("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*")
                || value.startsWith(".") || value.contains("..") || value.contains(".@")
                || value.substring(value.indexOf('@') + 1).matches("(?:.*\\.)?-.*|.*-(?:\\..*|$)")) {
            throw new IllegalArgumentException("Invalid email address");
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}
