package com.secureflow.identity.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({JwtKeyProperties.class, JwtClaimsProperties.class})
class JwtCryptographyConfiguration {
    @Bean
    RsaSigningKeys keys(JwtKeyProperties properties) {
        try {
            if (properties.privateKey() == null
                    || properties.publicKey() == null
                    || properties.keyId() == null
                    || !properties.keyId().matches("[A-Za-z0-9._-]{1,128}")) {
                throw new IllegalArgumentException();
            }
            var factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateCrtKey)
                    factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(properties.privateKey(), "PRIVATE KEY")));
            var publicKey = (RSAPublicKey)
                    factory.generatePublic(new X509EncodedKeySpec(readPem(properties.publicKey(), "PUBLIC KEY")));
            if (privateKey.getModulus().bitLength() < 2048
                    || publicKey.getModulus().bitLength() < 2048
                    || !privateKey.getModulus().equals(publicKey.getModulus())
                    || !privateKey.getPublicExponent().equals(publicKey.getPublicExponent())) {
                throw new IllegalArgumentException();
            }
            return new RsaSigningKeys(privateKey, publicKey, properties.keyId());
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT RSA key configuration");
        }
    }

    private static byte[] readPem(Path path, String label) throws Exception {
        var content = Files.readString(path, StandardCharsets.US_ASCII).trim();
        var begin = "-----BEGIN " + label + "-----";
        var end = "-----END " + label + "-----";
        if (!content.startsWith(begin) || !content.endsWith(end)) {
            throw new IllegalArgumentException();
        }
        var body = content.substring(begin.length(), content.length() - end.length())
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    @Bean
    JwtEncoder encoder(RsaSigningKeys keys, JwtKeyProperties properties) {
        var key = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(properties.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder decoder(RsaSigningKeys keys, JwtClaimsProperties properties) {
        return decoder(keys, properties, Clock.systemUTC());
    }

    JwtDecoder decoder(RsaSigningKeys keys, JwtClaimsProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        var converter = MappedJwtClaimSetConverter.withDefaults(java.util.Map.of());
        decoder.setClaimSetConverter(claims -> {
            for (var name : java.util.List.of("iss", "sub", "jti")) {
                if (claims.containsKey(name) && !(claims.get(name) instanceof String)) {
                    throw new BadJwtException("Invalid claim type");
                }
            }
            for (var name : java.util.List.of("iat", "nbf", "exp")) {
                if (claims.containsKey(name) && !(claims.get(name) instanceof java.util.Date)) {
                    throw new BadJwtException("Invalid timestamp type");
                }
            }
            var audience = claims.get("aud");
            if (audience != null
                    && (!(audience instanceof java.util.Collection<?> values)
                            || values.isEmpty()
                            || values.stream().anyMatch(value -> !(value instanceof String s) || s.isBlank()))) {
                throw new BadJwtException("Invalid audience type");
            }
            return converter.convert(claims);
        });
        var timestamps = new JwtTimestampValidator(AccessTokenValidator.SKEW);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        timestamps.setAllowEmptyNotBeforeClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                new AccessTokenValidator(clock),
                (Jwt jwt) -> keys.keyId().equals(jwt.getHeaders().get("kid"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder encoder, JwtClaimsProperties properties, RsaSigningKeys keys) {
        return new AccessTokenIssuer(encoder, properties, keys.keyId(), Clock.systemUTC());
    }

    record RsaSigningKeys(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey, String keyId) {
        @Override
        public String toString() {
            return "RsaSigningKeys[redacted]";
        }
    }
}
