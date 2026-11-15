package com.secureflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.secureflow.identity.application.SecurityStatePort;
import com.secureflow.identity.application.SecurityStateUnavailableException;
import com.secureflow.identity.infrastructure.BearerTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Import(BearerTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BearerHttpIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("secureflow.jwt.enabled", () -> true);
        r.add("secureflow.jwt.private-key", () -> LoginIntegrationTest.privateKey.toString());
        r.add("secureflow.jwt.public-key", () -> LoginIntegrationTest.publicKey.toString());
        r.add("secureflow.jwt.key-id", () -> "test-key");
        r.add("secureflow.jwt.issuer", () -> "https://issuer.example");
        r.add("secureflow.jwt.audience", () -> "secureflow");
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    JwtEncoder encoder;

    @MockitoSpyBean
    SecurityStatePort state;

    HttpResponse<String> request(String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null)
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        else request.GET();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    String value(String json, String name) {
        var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    String login() throws Exception {
        var name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"" + name + "\",\"email\":\"" + name
                        + "@example.com\",\"password\":\"correct horse battery\"}",
                null);
        assertThat(registered.statusCode()).isEqualTo(201);
        var login = request(
                "/api/v1/auth/login", "{\"identifier\":\"" + name + "\",\"password\":\"correct horse battery\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        return value(login.body(), "access_token");
    }

    @Test
    void authenticatesAndRejectsRevokedAndAnonymousRequests() throws Exception {
        var token = login();
        var jwt = decoder.decode(token);
        var sid = UUID.fromString(jwt.getClaimAsString("sid"));
        assertThat(jdbc.queryForObject("select user_id from session_families where id = ?", UUID.class, sid))
                .isEqualTo(UUID.fromString(jwt.getSubject()));
        var ok = request("/api/v1/me", null, token);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains(jwt.getSubject());
        assertThat(ok.headers().firstValue("set-cookie")).isEmpty();
        var anonymous = request("/api/v1/me", null, null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("www-authenticate").orElseThrow())
                .startsWith("Bearer");
        assertThat(request("/api/v1/me", null, "malformed").statusCode()).isEqualTo(401);
        assertThat(request("/api/v1/tasks", null, token).statusCode()).isEqualTo(403);
        jdbc.update("update session_families set revoked_at = created_at where id = ?", sid);
        assertThat(request("/api/v1/me", null, token).statusCode()).isEqualTo(401);
    }

    @Autowired
    com.secureflow.identity.application.RefreshSessionUseCase refresh;

    @Test
    void replayRevocationInvalidatesPreviouslyIssuedAccessToken() throws Exception {
        var name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        assertThat(request(
                                "/api/v1/auth/register",
                                "{\"username\":\"" + name + "\",\"email\":\"" + name
                                        + "@example.com\",\"password\":\"correct horse battery\"}",
                                null)
                        .statusCode())
                .isEqualTo(201);
        var response = request(
                "/api/v1/auth/login", "{\"identifier\":\"" + name + "\",\"password\":\"correct horse battery\"}", null);
        var original = value(response.body(), "refresh_token");
        var result = refresh.refresh(new com.secureflow.identity.application.RefreshSessionCommand(original));
        assertThat(request("/api/v1/me", null, result.accessToken()).statusCode())
                .isEqualTo(200);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> refresh.refresh(new com.secureflow.identity.application.RefreshSessionCommand(original)))
                .isInstanceOf(com.secureflow.identity.application.RefreshReplayException.class);
        assertThat(request("/api/v1/me", null, result.accessToken()).statusCode())
                .isEqualTo(401);
    }

    @Test
    void deactivationAndCatalogMismatchFailClosed() throws Exception {
        var token = login();
        var id = UUID.fromString(decoder.decode(token).getSubject());
        jdbc.update("update users set status = 'INACTIVE' where id = ?", id);
        assertThat(request("/api/v1/me", null, token).statusCode()).isEqualTo(401);
        jdbc.update("update users set status = 'ACTIVE' where id = ?", id);
        try {
            jdbc.update("update authorization_catalog_state set version = 2");
            assertThat(request("/api/v1/me", null, token).statusCode()).isEqualTo(503);
        } finally {
            jdbc.update("update authorization_catalog_state set version = 1");
        }
    }

    @Test
    void unavailableStateReturnsSanitized503() throws Exception {
        var token = login();
        doThrow(new SecurityStateUnavailableException()).when(state).findCurrentState(any(), any());
        var result = request("/api/v1/me", null, token);
        assertThat(result.statusCode()).isEqualTo(503);
        assertThat(result.body()).doesNotContain(token, "database", "SQL");
        assertThat(result.body()).doesNotContain(decoder.decode(token).getSubject());
        assertThat(result.headers().firstValue("set-cookie")).isEmpty();
        assertThat(request("/api/v1/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void distinctUsersRemainIsolatedAcrossConcurrentAndAnonymousRequests() throws Exception {
        var first = login();
        var second = login();
        var firstId = decoder.decode(first).getSubject();
        var secondId = decoder.decode(second).getSubject();
        assertThat(firstId).isNotEqualTo(secondId);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> request("/api/v1/me", null, first));
            var b = executor.submit(() -> request("/api/v1/me", null, second));
            assertThat(a.get().body()).contains(firstId).doesNotContain(secondId);
            assertThat(b.get().body()).contains(secondId).doesNotContain(firstId);
        }
        assertThat(request("/api/v1/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void appliesExactRoleAndPermissionAuthorities() throws Exception {
        var token = login();
        var response = request("/test/bearer/user", null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("ROLE_USER", "task:read:own")
                .doesNotContain("ROLE_ROLE_USER", "ROLE_ADMIN", "SCOPE_");
        assertThat(request("/test/bearer/admin", null, token).statusCode()).isEqualTo(403);
    }

    @Test
    void roleAssignmentInvalidatesOldSnapshotAndNewLoginReflectsRole() throws Exception {
        var old = login();
        var id = UUID.fromString(decoder.decode(old).getSubject());
        var name = jdbc.queryForObject("select username from users where id = ?", String.class, id);
        jdbc.update("insert into user_roles(user_id, role) values (?, 'AUDITOR')", id);
        jdbc.update("update users set security_version = security_version + 1 where id = ?", id);
        assertThat(request("/api/v1/me", null, old).statusCode()).isEqualTo(401);
        var response = request(
                "/api/v1/auth/login", "{\"identifier\":\"" + name + "\",\"password\":\"correct horse battery\"}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var renewed = value(response.body(), "access_token");
        var identity = request("/test/bearer/user", null, renewed);
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(identity.body()).contains("ROLE_AUDITOR", "task:read:any");
        var ordinary = login();
        var ordinaryId = decoder.decode(ordinary).getSubject();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var auditor = executor.submit(() -> request("/test/bearer/user", null, renewed));
            var user = executor.submit(() -> request("/test/bearer/user", null, ordinary));
            assertThat(auditor.get().body())
                    .contains(id.toString(), "ROLE_AUDITOR")
                    .doesNotContain(ordinaryId);
            assertThat(user.get().body())
                    .contains(ordinaryId, "ROLE_USER")
                    .doesNotContain(id.toString(), "ROLE_AUDITOR");
        }
    }

    @Test
    void rejectsSignedInvalidClaimsAndMissingSessions() throws Exception {
        var original = login();
        var jwt = decoder.decode(original);
        var another = decoder.decode(login());
        var variants = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var entry : java.util.Map.<String, Object>of(
                        "iss",
                        "https://wrong.example",
                        "aud",
                        java.util.List.of("wrong"),
                        "token_use",
                        "refresh",
                        "sid",
                        UUID.randomUUID().toString(),
                        "security_version",
                        999,
                        "catalog_version",
                        2)
                .entrySet()) {
            var claims = new java.util.HashMap<>(jwt.getClaims());
            claims.put(entry.getKey(), entry.getValue());
            variants.add(claims);
        }
        var missing = new java.util.HashMap<>(jwt.getClaims());
        missing.remove("sid");
        variants.add(missing);
        var otherSession = new java.util.HashMap<>(jwt.getClaims());
        otherSession.put("sid", another.getClaimAsString("sid"));
        variants.add(otherSession);
        var missingAccount = new java.util.HashMap<>(jwt.getClaims());
        missingAccount.put("sub", UUID.randomUUID().toString());
        variants.add(missingAccount);
        var expired = new java.util.HashMap<>(jwt.getClaims());
        var now = java.time.Instant.now();
        expired.put("iat", now.minusSeconds(180));
        expired.put("nbf", now.minusSeconds(180));
        expired.put("exp", now.minusSeconds(120));
        variants.add(expired);
        for (var claims : variants) {
            assertThat(request("/api/v1/me", null, sign(claims, "test-key")).statusCode())
                    .isEqualTo(401);
        }
        var parts = original.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThat(request("/api/v1/me", null, String.join(".", parts)).statusCode())
                .isEqualTo(401);
        var pem = java.nio.file.Files.readString(LoginIntegrationTest.privateKey)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        var key = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                        java.util.Base64.getDecoder().decode(pem)));
        var header = new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .keyID("wrong-key")
                .build();
        var wrongKeyId = new com.nimbusds.jwt.SignedJWT(
                header, com.nimbusds.jwt.SignedJWT.parse(original).getJWTClaimsSet());
        wrongKeyId.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey) key));
        assertThat(request("/api/v1/me", null, wrongKeyId.serialize()).statusCode())
                .isEqualTo(401);
    }

    @Test
    void changedSecurityVersionAndExpiredSessionInvalidateTokens() throws Exception {
        var token = login();
        var jwt = decoder.decode(token);
        var user = UUID.fromString(jwt.getSubject());
        var session = UUID.fromString(jwt.getClaimAsString("sid"));
        jdbc.update("update users set security_version = security_version + 1 where id = ?", user);
        assertThat(request("/api/v1/me", null, token).statusCode()).isEqualTo(401);
        jdbc.update("update users set security_version = security_version - 1 where id = ?", user);
        jdbc.update(
                "update session_families set created_at = '2020-01-01 00:00:00+00', expires_at = '2020-01-31 00:00:00+00' where id = ?",
                session);
        assertThat(request("/api/v1/me", null, token).statusCode()).isEqualTo(401);
    }

    String sign(java.util.Map<String, Object> claims, String keyId) {
        var header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .keyId(keyId)
                .build();
        var body =
                JwtClaimsSet.builder().claims(values -> values.putAll(claims)).build();
        return encoder.encode(JwtEncoderParameters.from(header, body)).getTokenValue();
    }
}
