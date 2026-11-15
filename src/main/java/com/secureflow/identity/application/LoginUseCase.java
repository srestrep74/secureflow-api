package com.secureflow.identity.application;

import com.secureflow.identity.domain.AccountStatus;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class LoginUseCase {
    private final AuthenticateCredentialsUseCase authentication;
    private final UserRepositoryPort users;
    private final SessionRepositoryPort sessions;
    private final LoginTokenPort tokens;
    private final LoginTransactionPort transaction;
    private final Clock clock;

    public LoginUseCase(
            AuthenticateCredentialsUseCase authentication,
            UserRepositoryPort users,
            SessionRepositoryPort sessions,
            LoginTokenPort tokens,
            LoginTransactionPort transaction,
            Clock clock) {
        this.authentication = authentication;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.transaction = transaction;
        this.clock = clock;
    }

    public LoginResult login(LoginCommand command) {
        var identity = authentication.authenticate(
                command == null ? null : new AuthenticateCredentialsCommand(command.identifier(), command.password()));
        return transaction.execute(identity.userId(), () -> {
            var user = users.findById(identity.userId()).orElseThrow(InvalidCredentialsException::new);
            if (user.status() != AccountStatus.ACTIVE) throw new InvalidCredentialsException();
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var family = SessionFamily.create(user.id(), now);
            var refresh = tokens.generateRefreshToken();
            var token = new RefreshTokenRecord(
                    UUID.randomUUID(), family.id(), tokens.digestRefreshToken(refresh), now, family.expiresAt(), null);
            sessions.create(family, token);
            return new LoginResult(tokens.issueAccessToken(user, family), refresh);
        });
    }
}
