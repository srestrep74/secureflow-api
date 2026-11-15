package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class RegistrationConfiguration {
    @Bean
    PasswordHashingPort passwordHashingPort(PasswordEncoder encoder) {
        return encoder::encode;
    }

    @Bean
    RegistrationTransactionPort registrationTransactionPort(TransactionTemplate transactions) {
        return operation -> transactions.executeWithoutResult(status -> operation.run());
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepositoryPort users,
            CredentialRepositoryPort credentials,
            PasswordHashingPort passwords,
            RegistrationTransactionPort transaction) {
        return new RegisterUserUseCase(users, credentials, passwords, transaction);
    }
}
