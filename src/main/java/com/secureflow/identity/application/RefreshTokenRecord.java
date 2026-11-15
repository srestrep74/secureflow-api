package com.secureflow.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshTokenRecord(
        UUID id, UUID familyId, String digest, Instant issuedAt, Instant expiresAt, Instant consumedAt) {
    public RefreshTokenRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(familyId);
        Objects.requireNonNull(digest);
        Objects.requireNonNull(issuedAt);
        Objects.requireNonNull(expiresAt);
        if (!digest.matches("[0-9a-f]{64}")
                || !expiresAt.isAfter(issuedAt)
                || (consumedAt != null && consumedAt.isBefore(issuedAt))) {
            throw new IllegalArgumentException("Invalid refresh token metadata");
        }
    }

    public boolean isUsableAt(Instant now, SessionFamily family) {
        return family.id().equals(familyId)
                && family.isActiveAt(now)
                && consumedAt == null
                && !now.isBefore(issuedAt)
                && now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "RefreshTokenRecord[redacted]";
    }
}
