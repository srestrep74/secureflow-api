package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.secureflow.identity.domain.Role;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.*;

class AccessTokenContractTest {
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID USER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SESSION = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private JwtCryptographyConfiguration configuration;
    private JwtCryptographyConfiguration.RsaSigningKeys keys;
    private JwtClaimsProperties properties;
    private JwtDecoder decoder;

    @TempDir
    Path directory;

    @BeforeEach
    void setup() throws Exception {
        var helper = new JwtCryptographyConfigurationTest();
        helper.directory = directory;
        helper.setup();
        configuration = helper.configuration;
        keys = configuration.keys(helper.properties());
        properties = helper.claimsProperties();
        decoder = configuration.decoder(keys, properties, CLOCK);
    }

    @Test
    void issuesAndDecodesCompleteToken() {
        var keyProperties = new JwtKeyProperties(null, null, "test-key");
        var issuer = new AccessTokenIssuer(configuration.encoder(keys, keyProperties), properties, "test-key", CLOCK);
        var token = decoder.decode(
                issuer.issue(new AccessTokenClaims(USER, SESSION, 7, 1, Set.of(Role.USER, Role.AUDITOR))));
        assertThat(token.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(token.getAudience()).containsExactly(properties.audience());
        assertThat(token.getSubject()).isEqualTo(USER.toString());
        assertThat(token.getClaimAsString("sid")).isEqualTo(SESSION.toString());
        assertThat(UUID.fromString(token.getId())).isNotNull();
        assertThat(token.getClaimAsString("token_use")).isEqualTo("access");
        assertThat(((Number) token.getClaim("security_version")).longValue()).isEqualTo(7);
        assertThat(((Number) token.getClaim("catalog_version")).longValue()).isEqualTo(1);
        assertThat(token.getClaimAsStringList("roles")).containsExactly("ROLE_AUDITOR", "ROLE_USER");
        assertThat(token.getClaimAsStringList("permissions"))
                .containsExactlyInAnyOrder(
                        "task:create", "task:read:own", "task:update:own", "task:delete:own", "task:read:any");
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(token.getNotBefore()).isEqualTo(NOW);
        assertThat(decoder.decode(issuer.issue(new AccessTokenClaims(USER, SESSION, 7, 1, Set.of(Role.USER))))
                        .getId())
                .isNotEqualTo(token.getId());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClaims")
    void rejectsInvalidClaims(String name, String claim, Object value) throws Exception {
        var claims = validClaims();
        if (value == null) claims.remove(claim);
        else claims.put(claim, value);
        assertThatThrownBy(() -> decoder.decode(sign(claims, "test-key", "JWT")))
                .isInstanceOf(JwtException.class);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidClaims() {
        long now = NOW.getEpochSecond();
        return Stream.of(
                args("missing issuer", "iss", null),
                args("wrong issuer", "iss", "https://wrong.example"),
                args("missing audience", "aud", null),
                args("wrong audience", "aud", List.of("other")),
                args("bad audience", "aud", List.of(42)),
                args("missing subject", "sub", null),
                args("invalid subject", "sub", "invalid"),
                args("subject type", "sub", 42),
                args("missing session", "sid", null),
                args("invalid session", "sid", "bad"),
                args("session type", "sid", 3),
                args("missing id", "jti", null),
                args("invalid id", "jti", "bad"),
                args("missing purpose", "token_use", null),
                args("wrong purpose", "token_use", "refresh"),
                args("missing expiration", "exp", null),
                args("expired", "exp", now - 31),
                args("bad expiration type", "exp", "tomorrow"),
                args("missing not-before", "nbf", null),
                args("future not-before", "nbf", now + 31),
                args("bad not-before type", "nbf", "tomorrow"),
                args("missing issued-at", "iat", null),
                args("bad issued-at type", "iat", "tomorrow"),
                args("future issued-at", "iat", now + 31),
                args("expiration before issuance", "exp", now),
                args("not-before after expiration", "nbf", now + 301),
                args("excessive lifetime", "exp", now + 301),
                args("missing security version", "security_version", null),
                args("negative security version", "security_version", -1),
                args("string security version", "security_version", "1"),
                args("missing catalog", "catalog_version", null),
                args("wrong catalog", "catalog_version", 2),
                args("negative catalog", "catalog_version", -1),
                args("bad roles type", "roles", "ROLE_USER"),
                args("unknown role", "roles", List.of("ROLE_ROOT")),
                args("duplicate role", "roles", List.of("ROLE_USER", "ROLE_USER")),
                args("bad permissions type", "permissions", "task:create"),
                args("unknown permission", "permissions", List.of("task:root")),
                args("missing permission", "permissions", null));
    }

    static org.junit.jupiter.params.provider.Arguments args(String name, String claim, Object value) {
        return org.junit.jupiter.params.provider.Arguments.of(name, claim, value);
    }

    @Test
    void rejectsWrongHeader() throws Exception {
        assertThatThrownBy(() -> decoder.decode(sign(validClaims(), "wrong-key", "JWT")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(validClaims(), "test-key", "at+jwt")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void appliesThirtySecondClockSkewWithoutExtendingDeclaredLifetime() throws Exception {
        var claims = validClaims();
        claims.put("iat", NOW.plusSeconds(30).getEpochSecond());
        claims.put("nbf", NOW.plusSeconds(30).getEpochSecond());
        claims.put("exp", NOW.plusSeconds(330).getEpochSecond());
        assertThat(decoder.decode(sign(claims, "test-key", "JWT")).getIssuedAt())
                .isEqualTo(NOW.plusSeconds(30));
        claims.put("iat", NOW.minusSeconds(310).getEpochSecond());
        claims.put("nbf", NOW.minusSeconds(310).getEpochSecond());
        claims.put("exp", NOW.minusSeconds(10).getEpochSecond());
        assertThat(decoder.decode(sign(claims, "test-key", "JWT")).getExpiresAt())
                .isEqualTo(NOW.minusSeconds(10));
    }

    @Test
    void rejectsInvalidInputAndCopiesRoles() {
        var roles = new HashSet<>(Set.of(Role.USER));
        var input = new AccessTokenClaims(USER, SESSION, 0, 1, roles);
        roles.clear();
        assertThat(input.roles()).containsExactly(Role.USER);
        assertThatThrownBy(() -> input.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AccessTokenClaims(USER, SESSION, -1, 1, Set.of(Role.USER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessTokenClaims(USER, SESSION, 0, 2, Set.of(Role.USER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Map<String, Object> validClaims() {
        var map = new HashMap<String, Object>();
        map.put("iss", properties.issuer());
        map.put("aud", List.of(properties.audience()));
        map.put("sub", USER.toString());
        map.put("sid", SESSION.toString());
        map.put("jti", UUID.randomUUID().toString());
        map.put("token_use", "access");
        map.put("iat", NOW.getEpochSecond());
        map.put("nbf", NOW.getEpochSecond());
        map.put("exp", NOW.plusSeconds(300).getEpochSecond());
        map.put("security_version", 0);
        map.put("catalog_version", 1);
        map.put("roles", List.of("ROLE_USER"));
        map.put("permissions", List.of("task:create", "task:read:own", "task:update:own", "task:delete:own"));
        return map;
    }

    private String sign(Map<String, Object> claims, String keyId, String type) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyId)
                .type(new JOSEObjectType(type))
                .build();
        var object = new JWSObject(header, new Payload(JSONObjectUtils.toJSONString(claims)));
        object.sign(new RSASSASigner((RSAPrivateKey) keys.privateKey()));
        return object.serialize();
    }
}
