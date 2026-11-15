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
            if (ex.getCause() == null || isUnavailable(ex)) throw new AuthenticationUnavailableException();
            throw ex;
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }
    }

    private static boolean isUnavailable(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.dao.DataAccessResourceFailureException
                    || cause instanceof org.springframework.transaction.CannotCreateTransactionException) return true;
        }
        return false;
    }
}
