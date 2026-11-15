package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionFamily(UUID id, UserId userId, Instant createdAt, Instant expiresAt, Instant revokedAt) {
    public static final Duration LIFETIME = Duration.ofDays(30);

    public SessionFamily {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        if (!expiresAt.equals(createdAt.plus(LIFETIME)) || (revokedAt != null && revokedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("Invalid session timestamps");
        }
    }

    public static SessionFamily create(UserId userId, Instant now) {
        return new SessionFamily(UUID.randomUUID(), userId, now, now.plus(LIFETIME), null);
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && !now.isBefore(createdAt) && now.isBefore(expiresAt);
    }
}
