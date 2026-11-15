package com.secureflow.identity.application;

import java.time.Instant;
import java.util.UUID;

public record SecurityStateSnapshot(
        boolean accountExists,
        boolean accountActive,
        long securityVersion,
        boolean sessionExists,
        UUID sessionOwnerId,
        Instant sessionCreatedAt,
        Instant sessionExpiresAt,
        boolean sessionRevoked,
        long catalogVersion) {}
