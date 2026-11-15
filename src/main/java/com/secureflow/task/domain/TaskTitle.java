package com.secureflow.task.domain;

import java.util.Objects;

public record TaskTitle(String value) {
    public TaskTitle {
        value = Objects.requireNonNull(value, "title").strip();
        if (value.isBlank() || value.length() > 200) throw new IllegalArgumentException("Invalid task title");
    }
}
