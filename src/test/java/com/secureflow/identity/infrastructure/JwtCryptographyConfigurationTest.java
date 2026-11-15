package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
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
        var token = configuration
                .encoder(keys, properties())
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId("test-key")
                                .build(),
                        JwtClaimsSet.builder()
                                .subject("synthetic")
                                .expiresAt(Instant.now().plusSeconds(60))
                                .build()));
        var decoded = configuration.decoder(keys).decode(token.getTokenValue());
        assertThat(decoded.getSubject()).isEqualTo("synthetic");
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "test-key");
    }

    @Test
    void rejectsDifferentKeyAndModifiedToken() throws Exception {
        var keys = configuration.keys(properties());
        var token = configuration
                .encoder(keys, properties())
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId("test-key")
                                .build(),
                        JwtClaimsSet.builder()
                                .subject("synthetic")
                                .expiresAt(Instant.now().plusSeconds(60))
                                .build()))
                .getTokenValue();
        var other = generate(2048);
        var otherPrivate =
                write("other-private.pem", "PRIVATE KEY", other.getPrivate().getEncoded());
        var otherPublic =
                write("other-public.pem", "PUBLIC KEY", other.getPublic().getEncoded());
        var otherKeys = configuration.keys(new JwtKeyProperties(otherPrivate, otherPublic, "test-key"));
        assertThatThrownBy(() -> configuration.decoder(otherKeys).decode(token)).isInstanceOf(JwtException.class);
        var parts = token.split("\\.");
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"tampered\"}".getBytes());
        assertThatThrownBy(() -> configuration.decoder(keys).decode(parts[0] + "." + payload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsupportedAlgorithm() {
        var keys = configuration.keys(properties());
        assertThatThrownBy(() -> configuration
                        .encoder(keys, properties())
                        .encode(JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS384).build(),
                                JwtClaimsSet.builder().subject("synthetic").build())))
                .isInstanceOf(JwtException.class);
        var jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS384).build(),
                JwtClaimsSet.builder()
                        .subject("synthetic")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()));
        assertThatThrownBy(() -> configuration.decoder(keys).decode(token.getTokenValue()))
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
                        "secureflow.jwt.key-id=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
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
