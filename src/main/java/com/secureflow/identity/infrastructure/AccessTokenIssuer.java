package com.secureflow.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

final class AccessTokenIssuer {
    static final Duration LIFETIME =
            Duration.ofSeconds(com.secureflow.identity.application.LoginResult.ACCESS_TOKEN_LIFETIME_SECONDS);
    private final JwtEncoder encoder;
    private final JwtClaimsProperties properties;
    private final String keyId;
    private final Clock clock;

    AccessTokenIssuer(JwtEncoder encoder, JwtClaimsProperties properties, String keyId, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.keyId = keyId;
        this.clock = clock;
    }

    String issue(AccessTokenClaims input) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(input.userId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(LIFETIME))
                .id(UUID.randomUUID().toString())
                .claim(AccessTokenValidator.SID, input.sessionFamilyId().toString())
                .claim(AccessTokenValidator.SECURITY_VERSION, input.securityVersion())
                .claim(AccessTokenValidator.CATALOG_VERSION, input.catalogVersion())
                .claim(AccessTokenValidator.PURPOSE, "access")
                .claim(
                        AccessTokenValidator.ROLES,
                        JwtAuthorityCatalog.roleNames(input.roles()).stream()
                                .sorted()
                                .toList())
                .claim(
                        AccessTokenValidator.PERMISSIONS,
                        JwtAuthorityCatalog.permissions(input.roles()).stream()
                                .sorted()
                                .toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId(keyId)
                                .type("JWT")
                                .build(),
                        claims))
                .getTokenValue();
    }
}
