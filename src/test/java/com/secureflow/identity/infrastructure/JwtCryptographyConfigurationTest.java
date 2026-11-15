package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtCryptographyConfigurationTest {
    @TempDir
    Path directory;

    KeyPair keyPair;
    Path privateKey;
    Path publicKey;
    JwtCryptographyConfiguration configuration = new JwtCryptographyConfiguration();

    @BeforeEach
    void setup() throws Exception {
        keyPair = generate(2048);
        privateKey = write("private.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        publicKey = write("public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    @Test
    void signsAndVerifiesWithConfiguredKeys() {
        var keys = configuration.keys(properties());
        var user = java.util.UUID.randomUUID();
        var issuer = new AccessTokenIssuer(
                configuration.encoder(keys, properties()), claimsProperties(), "test-key", java.time.Clock.systemUTC());
        var token = issuer.issue(new AccessTokenClaims(
                user, java.util.UUID.randomUUID(), 0, 1, java.util.Set.of(com.secureflow.identity.domain.Role.USER)));
        var decoded = configuration.decoder(keys, claimsProperties()).decode(token);
        assertThat(decoded.getSubject()).isEqualTo(user.toString());
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "test-key");
    }

    @Test
    void rejectsDifferentKeyAndModifiedToken() throws Exception {
        var keys = configuration.keys(properties());
        var token = new AccessTokenIssuer(
                        configuration.encoder(keys, properties()),
                        claimsProperties(),
                        "test-key",
                        java.time.Clock.systemUTC())
                .issue(new AccessTokenClaims(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        0,
                        1,
                        java.util.Set.of(com.secureflow.identity.domain.Role.USER)));
        assertThat(configuration.decoder(keys, claimsProperties()).decode(token))
                .isNotNull();
        var other = generate(2048);
        var otherPrivate =
                write("other-private.pem", "PRIVATE KEY", other.getPrivate().getEncoded());
        var otherPublic =
                write("other-public.pem", "PUBLIC KEY", other.getPublic().getEncoded());
        var otherKeys = configuration.keys(new JwtKeyProperties(otherPrivate, otherPublic, "test-key"));
        assertThatThrownBy(() ->
                        configuration.decoder(otherKeys, claimsProperties()).decode(token))
                .isInstanceOf(JwtException.class);
        var parts = token.split("\\.");
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"tampered\"}".getBytes());
        assertThatThrownBy(() -> configuration
                        .decoder(keys, claimsProperties())
                        .decode(parts[0] + "." + payload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsupportedAlgorithm() throws Exception {
        var keys = configuration.keys(properties());
        assertThatThrownBy(() -> configuration
                        .encoder(keys, properties())
                        .encode(JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS384).build(),
                                JwtClaimsSet.builder().subject("synthetic").build())))
                .isInstanceOf(JwtException.class);
        var valid = new AccessTokenIssuer(
                        configuration.encoder(keys, properties()),
                        claimsProperties(),
                        "test-key",
                        java.time.Clock.systemUTC())
                .issue(new AccessTokenClaims(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        0,
                        1,
                        java.util.Set.of(com.secureflow.identity.domain.Role.USER)));
        assertThat(configuration.decoder(keys, claimsProperties()).decode(valid))
                .isNotNull();
        var payload = com.nimbusds.jose.JWSObject.parse(valid).getPayload();
        var token = new com.nimbusds.jose.JWSObject(
                new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS384)
                        .keyID("test-key")
                        .type(com.nimbusds.jose.JOSEObjectType.JWT)
                        .build(),
                payload);
        token.sign(new com.nimbusds.jose.crypto.RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        assertThatThrownBy(() -> configuration.decoder(keys, claimsProperties()).decode(token.serialize()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsInvalidKeysWithoutLeakingContents() throws Exception {
        var secret = "sensitive-test-material";
        Files.writeString(privateKey, secret);
        assertThatThrownBy(() -> configuration.keys(properties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secret)
                .hasNoCause();
        privateKey = write("private.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Files.writeString(publicKey, secret);
        assertThatThrownBy(() -> configuration.keys(properties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secret)
                .hasNoCause();
    }

    @Test
    void rejectsWeakAndMismatchedKeys() throws Exception {
        var weak = generate(1024);
        var weakPrivate =
                write("weak-private.pem", "PRIVATE KEY", weak.getPrivate().getEncoded());
        var weakPublic = write("weak-public.pem", "PUBLIC KEY", weak.getPublic().getEncoded());
        assertThatThrownBy(() -> configuration.keys(new JwtKeyProperties(weakPrivate, weakPublic, "key")))
                .isInstanceOf(IllegalStateException.class);
        var other = generate(2048);
        var otherPublic =
                write("other-public.pem", "PUBLIC KEY", other.getPublic().getEncoded());
        assertThatThrownBy(() -> configuration.keys(new JwtKeyProperties(privateKey, otherPublic, "key")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enabledConfigurationFailsClosedAndDisabledConfigurationNeedsNoKeys() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(JwtActivationConfiguration.class, JwtCryptographyConfiguration.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
        runner.withPropertyValues("secureflow.jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("secureflow.jwt.enabled=tru")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "secureflow.jwt.enabled=true",
                        "secureflow.jwt.private-key=" + privateKey,
                        "secureflow.jwt.public-key=" + publicKey,
                        "secureflow.jwt.key-id=test-key",
                        "secureflow.jwt.issuer=https://issuer.example",
                        "secureflow.jwt.audience=secureflow")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    JwtClaimsProperties claimsProperties() {
        return new JwtClaimsProperties("https://issuer.example", "secureflow");
    }

    JwtKeyProperties properties() {
        return new JwtKeyProperties(privateKey, publicKey, "test-key");
    }

    static KeyPair generate(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    Path write(String name, String label, byte[] bytes) throws Exception {
        var pem = "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes) + "\n-----END " + label
                + "-----\n";
        return Files.writeString(directory.resolve(name), pem);
    }
}
