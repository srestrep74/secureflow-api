package com.secureflow.task.domain;

import java.util.Objects;
import java.util.UUID;

public record TaskId(UUID value) {
    public TaskId {
        Objects.requireNonNull(value, "task id");
    }
}
