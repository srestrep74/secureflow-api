package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.Objects;
import java.util.UUID;

public record TrustedSecurityClaims(UserId userId, UUID sessionFamilyId, long securityVersion, long catalogVersion) {
    public TrustedSecurityClaims {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionFamilyId);
        if (securityVersion < 0 || catalogVersion <= 0) throw new IllegalArgumentException("Invalid security claims");
    }
}
