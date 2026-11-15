package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.CredentialRepositoryPort;
import com.secureflow.identity.application.UserRepositoryPort;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

final class CredentialUserDetailsService implements UserDetailsService {
    private final UserRepositoryPort users;
    private final CredentialRepositoryPort credentials;

    CredentialUserDetailsService(UserRepositoryPort users, CredentialRepositoryPort credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        Username username = null;
        EmailAddress email = null;
        try {
            if (identifier == null) throw new IllegalArgumentException();
            if (identifier.contains("@")) email = new EmailAddress(identifier);
            else username = new Username(identifier);
        } catch (IllegalArgumentException ex) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var user = (email != null ? users.findByEmail(email) : users.findByUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var hash = credentials
                .findHash(user.id())
                .orElseThrow(() -> new InternalAuthenticationServiceException("Credential record unavailable"));
        var authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new CredentialPrincipal(
                user.id(), user.username().value(), hash, user.status() == AccountStatus.ACTIVE, authorities);
    }

    static final class CredentialPrincipal extends org.springframework.security.core.userdetails.User {
        private final UserId userId;

        CredentialPrincipal(
                UserId userId,
                String username,
                String hash,
                boolean enabled,
                java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            super(username, hash, enabled, true, true, true, authorities);
            this.userId = userId;
        }

        UserId userId() {
            return userId;
        }
    }
}
