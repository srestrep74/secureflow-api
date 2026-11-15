package com.secureflow.identity.application;

import com.secureflow.identity.domain.AccountStatus;
import com.secureflow.identity.domain.Role;
import com.secureflow.shared.domain.UserId;

public final class AdministratorBootstrap {
    private final ActiveAdministratorPolicy administrators;
    private final UserRepositoryPort users;
    private final CredentialRepositoryPort credentials;

    public AdministratorBootstrap(
            AdministratorGuardPort guard, UserRepositoryPort users, CredentialRepositoryPort credentials) {
        this.administrators = new ActiveAdministratorPolicy(guard);
        this.users = users;
        this.credentials = credentials;
    }

    public Outcome execute(UserId id) {
        administrators.lock();
        var user = users.findById(id).orElseThrow(() -> new IllegalStateException("Target is not eligible"));
        if (user.status() != AccountStatus.ACTIVE
                || credentials
                        .findHash(id)
                        .filter(AdministratorBootstrap::validHash)
                        .isEmpty()) {
            throw new IllegalStateException("Target is not eligible");
        }
        long count = administrators.count();
        if (count == 1 && user.roles().contains(Role.ADMIN)) return Outcome.ALREADY_ADMINISTRATOR;
        if (count != 0) throw new IllegalStateException("An active administrator already exists");
        long previousVersion = user.securityVersion();
        user.assignRole(Role.ADMIN);
        users.update(user, previousVersion);
        return Outcome.PROMOTED;
    }

    private static boolean validHash(String hash) {
        return hash.matches("\\$argon2id\\$v=19\\$m=[0-9]+,t=[0-9]+,p=[0-9]+\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+");
    }

    public enum Outcome {
        PROMOTED,
        ALREADY_ADMINISTRATOR
    }
}
