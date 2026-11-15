package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.Optional;

public interface CredentialRepositoryPort {
    void create(UserId userId, String passwordHash);

    Optional<String> findHash(UserId userId);
}
