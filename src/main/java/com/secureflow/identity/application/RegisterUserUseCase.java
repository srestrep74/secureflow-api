package com.secureflow.identity.application;

import com.secureflow.identity.domain.EmailAddress;
import com.secureflow.identity.domain.User;
import com.secureflow.identity.domain.Username;
import com.secureflow.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class RegisterUserUseCase {
    private final UserRepositoryPort users;
    private final CredentialRepositoryPort credentials;
    private final PasswordHashingPort passwords;
    private final RegistrationTransactionPort transaction;

    public RegisterUserUseCase(
            UserRepositoryPort users,
            CredentialRepositoryPort credentials,
            PasswordHashingPort passwords,
            RegistrationTransactionPort transaction) {
        this.users = users;
        this.credentials = credentials;
        this.passwords = passwords;
        this.transaction = transaction;
    }

    public RegistrationResult register(RegisterUserCommand command) {
        var username = new Username(command.username());
        var email = new EmailAddress(command.email());
        var password = command.password();
        if (password == null
                || password.isBlank()
                || password.length() > 256
                || password.codePointCount(0, password.length()) < 12
                || password.codePointCount(0, password.length()) > 128
                || password.getBytes(StandardCharsets.UTF_8).length > 512
                || !StandardCharsets.UTF_8.newEncoder().canEncode(password)) {
            throw new IllegalArgumentException("Invalid password");
        }
        var user = User.register(new UserId(UUID.randomUUID()), username, email);
        var hash = passwords.hash(password);
        transaction.execute(() -> {
            users.create(user);
            credentials.create(user.id(), hash);
        });
        return new RegistrationResult(user.id(), username.value(), email.value());
    }
}
