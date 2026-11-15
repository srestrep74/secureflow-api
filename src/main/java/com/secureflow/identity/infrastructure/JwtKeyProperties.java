package com.secureflow.identity.infrastructure;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureflow.jwt")
record JwtKeyProperties(Path privateKey, Path publicKey, String keyId) {}
