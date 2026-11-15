package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

public final class LogoutCurrentSessionUseCase {
    private final SessionRepositoryPort sessions;
    private final RefreshTransactionPort transaction;
    private final Clock clock;

    public LogoutCurrentSessionUseCase(
            SessionRepositoryPort sessions, RefreshTransactionPort transaction, Clock clock) {
        this.sessions = sessions;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void logout(LogoutCurrentSessionCommand command) {
        if (command == null || command.userId() == null || command.familyId() == null)
            throw new InvalidLogoutContextException();
        var userId = new UserId(command.userId());
        boolean valid;
        try {
            valid = transaction.executeForSession(userId, command.familyId(), () -> {
                var family = sessions.findFamily(command.familyId()).orElse(null);
                if (family == null || !family.userId().equals(userId)) return false;
                if (family.revokedAt() != null) return true;
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                if (!family.isActiveAt(now)) return false;
                if (!sessions.revoke(family.id(), now)) throw new IllegalStateException("Family revocation failed");
                return true;
            });
        } catch (RuntimeException ex) {
            throw new LogoutUnavailableException(ex);
        }
        if (!valid) throw new InvalidLogoutContextException();
    }
}
