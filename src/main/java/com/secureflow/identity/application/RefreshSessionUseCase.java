package com.secureflow.identity.application;

import com.secureflow.identity.domain.AccountStatus;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class RefreshSessionUseCase {
    private final UserRepositoryPort users;
    private final SessionRepositoryPort sessions;
    private final SecurityStatePort security;
    private final LoginTokenPort tokens;
    private final RefreshTransactionPort transaction;
    private final Clock clock;

    public RefreshSessionUseCase(
            UserRepositoryPort users,
            SessionRepositoryPort sessions,
            SecurityStatePort security,
            LoginTokenPort tokens,
            RefreshTransactionPort transaction,
            Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.security = security;
        this.tokens = tokens;
        this.transaction = transaction;
        this.clock = clock;
    }

    public RefreshSessionResult refresh(RefreshSessionCommand command) {
        String digest;
        try {
            digest = tokens.digestRefreshToken(command == null ? null : command.refreshToken());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRefreshTokenException();
        } catch (RuntimeException ex) {
            throw new RefreshUnavailableException(ex);
        }
        RefreshOutcome outcome;
        try {
            outcome = transaction.execute(digest, (userId, familyId) -> {
                var user = users.findById(userId).orElse(null);
                var family = sessions.findFamily(familyId).orElse(null);
                var token = sessions.findTokenByDigest(digest).orElse(null);
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                if (user == null
                        || family == null
                        || token == null
                        || !family.userId().equals(userId)
                        || !token.familyId().equals(familyId)
                        || !family.isActiveAt(now)
                        || now.isBefore(token.issuedAt())
                        || !now.isBefore(token.expiresAt())) return RefreshOutcome.invalid();
                if (token.consumedAt() != null) {
                    if (!sessions.revoke(familyId, now)) throw new IllegalStateException("Family revocation failed");
                    return RefreshOutcome.replay();
                }
                if (user.status() != AccountStatus.ACTIVE) return RefreshOutcome.invalid();
                var state = security.findCurrentState(userId, familyId);
                if (!state.accountExists()
                        || !state.accountActive()
                        || !state.sessionExists()
                        || !userId.value().equals(state.sessionOwnerId())
                        || state.sessionRevoked()
                        || state.securityVersion() != user.securityVersion()) return RefreshOutcome.invalid();
                var secret = tokens.generateRefreshToken();
                var successor = new RefreshTokenRecord(
                        UUID.randomUUID(), familyId, tokens.digestRefreshToken(secret), now, family.expiresAt(), null);
                sessions.rotate(token, successor, now);
                return RefreshOutcome.success(new RefreshSessionResult(tokens.issueAccessToken(user, family), secret));
            });
            if (outcome == null) throw new RefreshUnavailableException();
        } catch (RuntimeException ex) {
            throw new RefreshUnavailableException(ex);
        }
        return switch (outcome.status()) {
            case INVALID -> throw new InvalidRefreshTokenException();
            case REPLAY -> throw new RefreshReplayException();
            case SUCCESS -> outcome.result();
        };
    }
}
