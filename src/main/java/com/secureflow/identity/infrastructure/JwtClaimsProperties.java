package com.secureflow.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureflow.jwt")
record JwtClaimsProperties(String issuer, String audience) {
    JwtClaimsProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
    }
}
