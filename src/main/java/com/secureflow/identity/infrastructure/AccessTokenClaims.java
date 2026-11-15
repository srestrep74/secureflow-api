package com.secureflow.identity.infrastructure;

import com.secureflow.identity.domain.Role;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record AccessTokenClaims(
        UUID userId, UUID sessionFamilyId, long securityVersion, long catalogVersion, Set<Role> roles) {
    AccessTokenClaims {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionFamilyId);
        roles = Set.copyOf(roles);
        if (securityVersion < 0 || catalogVersion != JwtAuthorityCatalog.VERSION || roles.isEmpty()) {
            throw new IllegalArgumentException("Invalid access token claims");
        }
    }
}
