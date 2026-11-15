package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.CurrentLogoutContext;
import com.secureflow.identity.application.InvalidLogoutContextException;
import com.secureflow.identity.application.LogoutCurrentSessionCommand;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class SpringCurrentLogoutContext implements CurrentLogoutContext {
    @Override
    public LogoutCurrentSessionCommand current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new InvalidLogoutContextException();
        }
        var jwt = token.getToken();
        return new LogoutCurrentSessionCommand(
                UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
    }
}
