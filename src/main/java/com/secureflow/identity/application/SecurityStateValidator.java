package com.secureflow.identity.application;

import java.time.Clock;
import java.util.Objects;

public final class SecurityStateValidator {
    private final SecurityStatePort state;
    private final Clock clock;

    public SecurityStateValidator(SecurityStatePort state, Clock clock) {
        this.state = Objects.requireNonNull(state);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean isValid(TrustedSecurityClaims claims) {
        var snapshot = state.findCurrentState(claims.userId(), claims.sessionFamilyId());
        var now = clock.instant();
        return snapshot.accountExists()
                && snapshot.accountActive()
                && snapshot.securityVersion() == claims.securityVersion()
                && snapshot.sessionExists()
                && claims.userId().value().equals(snapshot.sessionOwnerId())
                && !snapshot.sessionRevoked()
                && snapshot.sessionCreatedAt() != null
                && snapshot.sessionExpiresAt() != null
                && !now.isBefore(snapshot.sessionCreatedAt())
                && now.isBefore(snapshot.sessionExpiresAt())
                && snapshot.catalogVersion() > 0
                && snapshot.catalogVersion() == claims.catalogVersion();
    }
}
