package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class CredentialAuthenticationConfiguration {
    @Bean
    UserDetailsService credentialUserDetailsService(UserRepositoryPort users, CredentialRepositoryPort credentials) {
        return new CredentialUserDetailsService(users, credentials);
    }

    @Bean
    AuthenticationManager credentialAuthenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    CredentialAuthenticationPort credentialAuthenticationPort(AuthenticationManager manager) {
        return new SpringCredentialAuthenticationAdapter(manager);
    }

    @Bean
    AuthenticateCredentialsUseCase authenticateCredentialsUseCase(CredentialAuthenticationPort port) {
        return new AuthenticateCredentialsUseCase(port);
    }
}
