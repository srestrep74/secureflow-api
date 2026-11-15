package com.secureflow.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepositoryPort {
    void create(SessionFamily family, RefreshTokenRecord initialToken);

    Optional<SessionFamily> findFamily(UUID id);

    Optional<RefreshTokenRecord> findTokenByDigest(String digest);

    void rotate(RefreshTokenRecord current, RefreshTokenRecord successor, Instant consumedAt);

    boolean revoke(UUID familyId, Instant revokedAt);
}
