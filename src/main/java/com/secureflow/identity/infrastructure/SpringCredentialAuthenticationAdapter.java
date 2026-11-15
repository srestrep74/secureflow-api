package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

final class SpringCredentialAuthenticationAdapter implements CredentialAuthenticationPort {
    private final AuthenticationManager manager;

    SpringCredentialAuthenticationAdapter(AuthenticationManager manager) {
        this.manager = manager;
    }

    @Override
    public AuthenticatedIdentity authenticate(String identifier, String password) {
        try {
            var authentication =
                    manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier, password));
            var principal = (CredentialUserDetailsService.CredentialPrincipal) authentication.getPrincipal();
            return new AuthenticatedIdentity(principal.userId(), principal.getUsername());
        } catch (InternalAuthenticationServiceException ex) {
            throw new AuthenticationUnavailableException();
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        } catch (RuntimeException ex) {
            throw new AuthenticationUnavailableException();
        }
    }
}
