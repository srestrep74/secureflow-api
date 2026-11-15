package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.AdministratorBootstrap;
import com.secureflow.identity.application.AdministratorGuardPort;
import com.secureflow.identity.application.CredentialRepositoryPort;
import com.secureflow.identity.application.UserRepositoryPort;
import com.secureflow.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministratorBootstrapService {
    private final AdministratorBootstrap bootstrap;

    public AdministratorBootstrapService(
            AdministratorGuardPort guard, UserRepositoryPort users, CredentialRepositoryPort credentials) {
        bootstrap = new AdministratorBootstrap(guard, users, credentials);
    }

    @Transactional
    public AdministratorBootstrap.Outcome execute(UserId id) {
        return bootstrap.execute(id);
    }
}
