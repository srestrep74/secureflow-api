package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.SecurityStateUnavailableException;
import com.secureflow.identity.application.SecurityStateValidator;
import com.secureflow.identity.application.TrustedSecurityClaims;
import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class StatefulJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    static final class SecurityStateAuthenticationException extends AuthenticationException {
        SecurityStateAuthenticationException() {
            super("Security state unavailable");
        }
    }

    private final SecurityStateValidator validator;

    StatefulJwtAuthenticationConverter(SecurityStateValidator validator) {
        this.validator = validator;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        var claims = new TrustedSecurityClaims(
                new UserId(UUID.fromString(jwt.getSubject())),
                UUID.fromString(jwt.getClaimAsString(AccessTokenValidator.SID)),
                ((Number) jwt.getClaim(AccessTokenValidator.SECURITY_VERSION)).longValue(),
                ((Number) jwt.getClaim(AccessTokenValidator.CATALOG_VERSION)).longValue());
        try {
            if (!validator.isValid(claims)) throw new InvalidBearerTokenException("Invalid access token");
        } catch (SecurityStateUnavailableException ex) {
            throw new SecurityStateAuthenticationException();
        }
        var authorities = Stream.concat(
                        jwt.getClaimAsStringList(AccessTokenValidator.ROLES).stream(),
                        jwt.getClaimAsStringList(AccessTokenValidator.PERMISSIONS).stream())
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
