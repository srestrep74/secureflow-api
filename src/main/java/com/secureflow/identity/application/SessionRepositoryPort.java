package com.secureflow.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepositoryPort {
    void create(SessionFamily family, RefreshTokenRecord initialToken);

    Optional<SessionFamily> findFamily(UUID id);

    Optional<RefreshTokenRecord> findTokenByDigest(String digest);

    boolean revoke(UUID familyId, Instant revokedAt);
}
