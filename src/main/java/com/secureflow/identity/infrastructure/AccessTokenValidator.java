package com.secureflow.identity.infrastructure;

import com.secureflow.identity.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {
    static final String SID = "sid",
            SECURITY_VERSION = "security_version",
            CATALOG_VERSION = "catalog_version",
            PURPOSE = "token_use",
            ROLES = "roles",
            PERMISSIONS = "permissions";
    static final Duration SKEW = Duration.ofSeconds(30);
    private final Clock clock;

    AccessTokenValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (!"JWT".equals(jwt.getHeaders().get("typ"))
                    || !"access".equals(jwt.getClaims().get(PURPOSE))) return invalid();
            uuid(jwt.getClaims().get("sub"));
            uuid(jwt.getClaims().get("jti"));
            uuid(jwt.getClaims().get(SID));
            if (version(jwt.getClaims().get(SECURITY_VERSION)) < 0
                    || version(jwt.getClaims().get(CATALOG_VERSION)) != JwtAuthorityCatalog.VERSION) return invalid();
            Instant issued = jwt.getIssuedAt(), before = jwt.getNotBefore(), expires = jwt.getExpiresAt();
            if (issued == null
                    || before == null
                    || expires == null
                    || !expires.isAfter(issued)
                    || before.isAfter(expires)
                    || issued.isAfter(clock.instant().plus(SKEW))
                    || Duration.between(issued, expires).compareTo(AccessTokenIssuer.LIFETIME) > 0) return invalid();
            Set<String> names = strings(jwt.getClaims().get(ROLES));
            Set<Role> roles = new HashSet<>();
            for (String name : names) {
                if (!name.startsWith("ROLE_")) return invalid();
                roles.add(Role.valueOf(name.substring(5)));
            }
            if (roles.isEmpty()
                    || !JwtAuthorityCatalog.permissions(roles)
                            .equals(strings(jwt.getClaims().get(PERMISSIONS)))) return invalid();
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ex) {
            return invalid();
        }
    }

    private static void uuid(Object value) {
        if (!(value instanceof String text) || !UUID.fromString(text).toString().equals(text))
            throw new IllegalArgumentException();
    }

    private static long version(Object value) {
        if (!(value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException();
        return ((Number) value).longValue();
    }

    private static Set<String> strings(Object value) {
        if (!(value instanceof Collection<?> list)) throw new IllegalArgumentException();
        Set<String> result = new HashSet<>();
        for (Object item : list)
            if (!(item instanceof String s) || !result.add(s)) throw new IllegalArgumentException();
        return result;
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
