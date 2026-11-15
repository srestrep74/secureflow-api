package com.secureflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.secureflow.identity.application.SessionRepositoryPort;
import com.secureflow.identity.infrastructure.RefreshTokenSecrets;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginHttpIntegrationTest {
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
        r.add("springdoc.api-docs.enabled", () -> true);
    }

    @LocalServerPort
    int port;

    @MockitoSpyBean
    SessionRepositoryPort sessions;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    @Autowired
    RefreshTokenSecrets secrets;

    HttpResponse<String> request(String path, String body, String contentType) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Content-Type", contentType)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    HttpResponse<String> login(String body) throws Exception {
        return request("/api/v1/auth/login", body, "application/json");
    }

    String value(String json, String name) {
        var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void refreshRotatesAndReplayCommitsRevocation() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"refreshuser\",\"email\":\"refresh@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registered.statusCode()).isEqualTo(201);
        var userId = UUID.fromString(value(registered.body(), "id"));
        var login = login("{\"identifier\":\"refreshuser\",\"password\":\"correct horse battery\"}");
        var access1 = value(login.body(), "access_token");
        var refresh1 = value(login.body(), "refresh_token");
        var family = UUID.fromString(decoder.decode(access1).getClaimAsString("sid"));
        var expiration = jdbc.queryForObject(
                "select expires_at from session_families where id = ?", java.sql.Timestamp.class, family);
        var claims = new java.util.HashMap<>(decoder.decode(access1).getClaims());
        var now = java.time.Instant.now();
        claims.put("iat", now.minusSeconds(180));
        claims.put("nbf", now.minusSeconds(180));
        claims.put("exp", now.minusSeconds(120));
        var expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(
                                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                                .keyId("test-key")
                                .type("JWT")
                                .build(),
                        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                                .claims(values -> values.putAll(claims))
                                .build()))
                .getTokenValue();
        try (var client = HttpClient.newHttpClient()) {
            var rejected = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/refresh"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + expired)
                            .POST(HttpRequest.BodyPublishers.ofString("{\"refresh_token\":\"" + refresh1 + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(401);
        }
        var rotated = request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refresh1 + "\"}", "application/json");
        assertThat(rotated.statusCode()).isEqualTo(200);
        assertThat(rotated.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(rotated.headers().firstValue("pragma").orElseThrow()).isEqualTo("no-cache");
        assertThat(rotated.headers().firstValue("set-cookie")).isEmpty();
        assertThat(rotated.body())
                .contains("\"token_type\":\"Bearer\"", "\"expires_in\":300")
                .doesNotContain("correct horse battery", "PRIVATE KEY");
        var access2 = value(rotated.body(), "access_token");
        var refresh2 = value(rotated.body(), "refresh_token");
        assertThat(access2).isNotEqualTo(access1);
        assertThat(refresh2).isNotEqualTo(refresh1);
        var decoded2 = decoder.decode(access2);
        assertThat(decoded2.getClaimAsString("sid")).isEqualTo(family.toString());
        assertThat(decoded2.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded2.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(java.time.Duration.between(decoded2.getIssuedAt(), decoded2.getExpiresAt())
                        .toSeconds())
                .isEqualTo(300);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ? and consumed_at is null and token_digest = ?",
                        Integer.class,
                        family,
                        secrets.digest(refresh2)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ? and consumed_at is not null and token_digest = ?",
                        Integer.class,
                        family,
                        secrets.digest(refresh1)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where token_digest = ?", Integer.class, refresh2))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select expires_at from session_families where id = ?", java.sql.Timestamp.class, family))
                .isEqualTo(expiration);
        assertThat(jdbc.queryForObject(
                        "select count(*) from session_families where user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(me(access1).statusCode()).isEqualTo(200);
        var principal = me(access2);
        assertThat(principal.statusCode()).isEqualTo(200);
        assertThat(value(principal.body(), "id")).isEqualTo(userId.toString());
        var replay = request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refresh1 + "\"}", "application/json");
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(replay.body()).doesNotContain(refresh1, "replay", "consumed");
        assertThat(jdbc.queryForObject(
                        "select revoked_at is not null from session_families where id = ?", Boolean.class, family))
                .isTrue();
        assertThat(me(access2).statusCode()).isEqualTo(401);
        assertThat(request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refresh2 + "\"}", "application/json")
                        .statusCode())
                .isEqualTo(401);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ?", Integer.class, family))
                .isEqualTo(2);
    }

    HttpResponse<String> me(String access) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                            .header("Authorization", "Bearer " + access)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void rejectsInactiveExpiredAndRevokedSessionsWithoutIssuingSuccessors() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"refreshstate\",\"email\":\"refreshstate@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var user = UUID.fromString(value(registered.body(), "id"));
        var login = login("{\"identifier\":\"refreshstate\",\"password\":\"correct horse battery\"}");
        var token = value(login.body(), "refresh_token");
        var family = UUID.fromString(
                decoder.decode(value(login.body(), "access_token")).getClaimAsString("sid"));
        var body = "{\"refresh_token\":\"" + token + "\"}";
        jdbc.update("update users set status = 'INACTIVE' where id = ?", user);
        var inactive = request("/api/v1/auth/refresh", body, "application/json");
        assertThat(inactive.statusCode()).isEqualTo(401);
        jdbc.update("update users set status = 'ACTIVE' where id = ?", user);
        jdbc.update(
                "update refresh_tokens set issued_at = now() - interval '2 days', expires_at = now() - interval '1 day' where family_id = ?",
                family);
        var expired = request("/api/v1/auth/refresh", body, "application/json");
        assertThat(expired.statusCode()).isEqualTo(401);
        jdbc.update(
                "update refresh_tokens set issued_at = f.created_at, expires_at = f.expires_at from session_families f where refresh_tokens.family_id = f.id and f.id = ?",
                family);
        jdbc.update("update session_families set revoked_at = created_at where id = ?", family);
        var revoked = request("/api/v1/auth/refresh", body, "application/json");
        assertThat(revoked.statusCode()).isEqualTo(401);
        jdbc.update(
                "update session_families set revoked_at = null, created_at = now() - interval '31 days', expires_at = now() - interval '1 day' where id = ?",
                family);
        var expiredFamily = request("/api/v1/auth/refresh", body, "application/json");
        assertThat(expiredFamily.statusCode()).isEqualTo(401);
        assertThat(expired.body())
                .isEqualTo(inactive.body())
                .isEqualTo(revoked.body())
                .isEqualTo(expiredFamily.body())
                .doesNotContain(token);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ?", Integer.class, family))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, user))
                .isEqualTo(1);
    }

    @Test
    void unexpectedRefreshFailureIsNotClassifiedAsUnavailable() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"refreshfailure\",\"email\":\"refreshfailure@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registered.statusCode()).isEqualTo(201);
        var login = login("{\"identifier\":\"refreshfailure\",\"password\":\"correct horse battery\"}");
        var token = value(login.body(), "refresh_token");
        doThrow(new IllegalStateException("sensitive failure")).when(sessions).rotate(any(), any(), any());
        var response = request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + token + "\"}", "application/json");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain(token, "sensitive failure", "access_token", "refresh_token");
        assertThat(jdbc.queryForObject(
                        "select consumed_at is null from refresh_tokens where token_digest = ?",
                        Boolean.class,
                        secrets.digest(token)))
                .isTrue();
    }

    @Test
    void refreshInfrastructureFailureDoesNotReturnTokens() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"refreshoutage\",\"email\":\"refreshoutage@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registered.statusCode()).isEqualTo(201);
        var login = login("{\"identifier\":\"refreshoutage\",\"password\":\"correct horse battery\"}");
        var token = value(login.body(), "refresh_token");
        doThrow(new DataAccessResourceFailureException("simulated outage"))
                .when(sessions)
                .rotate(any(), any(), any());
        var response = request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + token + "\"}", "application/json");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain(token, "outage", "access_token", "refresh_token");
        assertThat(jdbc.queryForObject(
                        "select consumed_at is null from refresh_tokens where token_digest = ?",
                        Boolean.class,
                        secrets.digest(token)))
                .isTrue();
    }

    @Test
    void rejectsInvalidRefreshBodies() throws Exception {
        for (var body : new String[] {
            "",
            "{}",
            "{\"refresh_token\":null}",
            "{\"refresh_token\":\" \"}",
            "{",
            "{\"refresh_token\":\"x\",\"role\":\"ADMIN\"}",
            "{\"refresh_token\":\"" + "a".repeat(257) + "\"}"
        }) {
            assertThat(request("/api/v1/auth/refresh", body, "application/json").statusCode())
                    .isEqualTo(400);
        }
        var invalid = request("/api/v1/auth/refresh", "{\"refresh_token\":\"secret-invalid\"}", "application/json");
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(invalid.body()).doesNotContain("secret-invalid");
        assertThat(request("/api/v1/auth/refresh", "{\"refresh_token\":123}", "application/json")
                        .statusCode())
                .isEqualTo(400);
        assertThat(request(
                                "/api/v1/auth/refresh",
                                "{\"refresh_token\":\"" + "a".repeat(5000) + "\"}",
                                "application/json")
                        .statusCode())
                .isEqualTo(400);
        assertThat(request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + "a".repeat(43) + "\"}", "application/json")
                        .statusCode())
                .isEqualTo(401);
        assertThat(request("/api/v1/auth/refresh", "x", "text/plain").statusCode())
                .isEqualTo(415);
    }

    @Test
    void verifiesHttpDeliveryAndFailures() throws Exception {
        var registration = request(
                "/api/v1/auth/register",
                "{\"username\":\"httpauth\",\"email\":\"httpauth@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registration.statusCode()).isEqualTo(201);
        var id = value(registration.body(), "id");
        var response = login("{\"identifier\":\"httpauth@example.com\",\"password\":\"correct horse battery\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("application/json");
        assertThat(response.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("pragma").orElseThrow()).isEqualTo("no-cache");
        assertThat(response.headers().firstValue("set-cookie")).isEmpty();
        assertThat(response.body())
                .contains("\"token_type\":\"Bearer\"", "\"expires_in\":300")
                .doesNotContain("correct horse battery", "argon2", "PRIVATE KEY");
        var access = value(response.body(), "access_token");
        var refresh = value(response.body(), "refresh_token");
        var jwt = decoder.decode(access);
        var sid = UUID.fromString(jwt.getClaimAsString("sid"));
        assertThat(jwt.getSubject()).isEqualTo(id);
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://issuer.example");
        assertThat(jwt.getAudience()).contains("secureflow");
        assertThat(jwt.getClaimAsStringList("roles")).contains("ROLE_USER");
        assertThat(jwt.getClaimAsStringList("permissions")).contains("task:read:own");
        assertThat(jdbc.queryForObject("select user_id from session_families where id = ?", UUID.class, sid))
                .isEqualTo(UUID.fromString(id));
        assertThat(jdbc.queryForObject(
                        "select token_digest from refresh_tokens where family_id = ?", String.class, sid))
                .isEqualTo(secrets.digest(refresh))
                .isNotEqualTo(refresh);
        var wrong = login("{\"identifier\":\"httpauth\",\"password\":\"wrong\"}");
        var unknown = login("{\"identifier\":\"unknown\",\"password\":\"wrong\"}");
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.body()).isEqualTo(wrong.body());
        jdbc.update("update users set status = 'INACTIVE' where id = ?", UUID.fromString(id));
        assertThat(login("{\"identifier\":\"httpauth\",\"password\":\"correct horse battery\"}")
                        .body())
                .isEqualTo(wrong.body());
        assertThat(jdbc.queryForObject(
                        "select count(*) from session_families where user_id = ?", Integer.class, UUID.fromString(id)))
                .isEqualTo(1);
        assertThat(wrong.body()).doesNotContain("wrong", access, refresh);
        try (var client = HttpClient.newHttpClient()) {
            var denied = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/tasks"))
                            .header("Authorization", "Bearer " + access)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isIn(401, 403);
            var docs = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(docs.body())
                    .contains(
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "bearerAuth",
                            "access_token",
                            "refresh_token");
        }
    }

    @Test
    void classifiedPersistenceOutageReturns503WithoutSession() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"outageuser\",\"email\":\"outage@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registered.statusCode()).isEqualTo(201);
        var id = UUID.fromString(value(registered.body(), "id"));
        doThrow(new DataAccessResourceFailureException("simulated outage"))
                .when(sessions)
                .create(any(), any());
        var result = login("{\"identifier\":\"outageuser\",\"password\":\"correct horse battery\"}");
        assertThat(result.statusCode()).isEqualTo(503);
        assertThat(result.body()).doesNotContain("outage", "correct horse battery", "access_token", "refresh_token");
        assertThat(jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void unexpectedPersistenceFailureReturns500WithoutSession() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"failureuser\",\"email\":\"failureuser@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        assertThat(registered.statusCode()).isEqualTo(201);
        var id = UUID.fromString(value(registered.body(), "id"));
        doThrow(new IllegalArgumentException("sensitive failure"))
                .when(sessions)
                .create(any(), any());
        var result = login("{\"identifier\":\"failureuser\",\"password\":\"correct horse battery\"}");
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.body())
                .doesNotContain("sensitive failure", "correct horse battery", "access_token", "refresh_token");
        assertThat(jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        for (var body : new String[] {
            "{}",
            "{",
            "{\"identifier\":\"a\"}",
            "{\"password\":\"secret\"}",
            "{\"identifier\":\"a\",\"password\":\"secret\",\"role\":\"ADMIN\"}",
            "{\"identifier\":\"" + "a".repeat(5000) + "\",\"password\":\"secret\"}"
        }) {
            var result = login(body);
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.body()).doesNotContain("secret");
        }
        assertThat(login("{\"identifier\":\"!\",\"password\":\"secret\"}").statusCode())
                .isEqualTo(401);
        try (var client = HttpClient.newHttpClient()) {
            var result = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).isIn(401, 403, 405);
        }
        assertThat(request("/api/v1/auth/login", "test", "text/plain").statusCode())
                .isEqualTo(415);
    }

    HttpResponse<String> logout(String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/logout"));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    builder.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void httpLogoutRevokesOnlyCurrentFamilyAndCompletesLifecycle() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"logoutuser\",\"email\":\"logout@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var user = UUID.fromString(value(registered.body(), "id"));
        var credentials = "{\"identifier\":\"logoutuser\",\"password\":\"correct horse battery\"}";
        var first = login(credentials);
        var second = login(credentials);
        var access = value(first.body(), "access_token");
        var refresh = value(first.body(), "refresh_token");
        var otherAccess = value(second.body(), "access_token");
        var otherRefresh = value(second.body(), "refresh_token");
        var family = UUID.fromString(decoder.decode(access).getClaimAsString("sid"));
        var userBefore = jdbc.queryForMap("select status, security_version from users where id = ?", user);
        var rolesBefore = jdbc.queryForList("select role from user_roles where user_id = ? order by role", user);
        assertThat(me(access).statusCode()).isEqualTo(200);
        assertThat(me(otherAccess).statusCode()).isEqualTo(200);
        assertThat(logout(null).statusCode()).isEqualTo(401);
        assertThat(logout("malformed").statusCode()).isEqualTo(401);
        var result = logout(access);
        assertThat(result.statusCode()).isEqualTo(204);
        assertThat(result.body()).isEmpty();
        assertThat(result.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(result.headers().firstValue("set-cookie")).isEmpty();
        var revoked =
                jdbc.queryForMap("select revoked_at, xmin::text as version from session_families where id = ?", family);
        assertThat(revoked.get("revoked_at")).isNotNull();
        assertThat(logout(access).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForMap(
                        "select revoked_at, xmin::text as version from session_families where id = ?", family))
                .isEqualTo(revoked);
        var rejected = me(access);
        assertThat(rejected.statusCode()).isEqualTo(401);
        assertThat(rejected.body()).doesNotContain(access, family.toString(), user.toString());
        assertThat(me(otherAccess).statusCode()).isEqualTo(200);
        var invalidRefresh =
                request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refresh + "\"}", "application/json");
        assertThat(invalidRefresh.statusCode()).isEqualTo(401);
        assertThat(invalidRefresh.body()).doesNotContain(refresh, "access_token", "refresh_token");
        var rotated =
                request("/api/v1/auth/refresh", "{\"refresh_token\":\"" + otherRefresh + "\"}", "application/json");
        assertThat(rotated.statusCode()).isEqualTo(200);
        assertThat(me(value(rotated.body(), "access_token")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, user))
                .isEqualTo(2);
        assertThat(jdbc.queryForMap("select status, security_version from users where id = ?", user))
                .isEqualTo(userBefore);
        assertThat(jdbc.queryForList("select role from user_roles where user_id = ? order by role", user))
                .isEqualTo(rolesBefore);
        var newAccess = value(login(credentials).body(), "access_token");
        assertThat(me(newAccess).statusCode()).isEqualTo(200);
        assertThat(logout(value(rotated.body(), "access_token")).statusCode()).isEqualTo(204);
        assertThat(request(
                                "/api/v1/auth/refresh",
                                "{\"refresh_token\":\"" + value(rotated.body(), "refresh_token") + "\"}",
                                "application/json")
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    void httpLogoutReportsPersistenceFailureWithoutFalseSuccess() throws Exception {
        request(
                "/api/v1/auth/register",
                "{\"username\":\"logoutfailure\",\"email\":\"logoutfailure@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var token = value(
                login("{\"identifier\":\"logoutfailure\",\"password\":\"correct horse battery\"}")
                        .body(),
                "access_token");
        var family = UUID.fromString(decoder.decode(token).getClaimAsString("sid"));
        doThrow(new DataAccessResourceFailureException("sensitive outage"))
                .when(sessions)
                .revoke(any(), any());
        var response = logout(token);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain(token, family.toString(), "sensitive", "SQL");
        assertThat(jdbc.queryForObject(
                        "select revoked_at is null from session_families where id = ?", Boolean.class, family))
                .isTrue();
    }

    @Test
    void logoutRejectsInvalidCredentialsAndUnsupportedMethods() throws Exception {
        var registered = request(
                "/api/v1/auth/register",
                "{\"username\":\"logoutinvalid\",\"email\":\"logoutinvalid@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var user = UUID.fromString(value(registered.body(), "id"));
        var access = value(
                login("{\"identifier\":\"logoutinvalid\",\"password\":\"correct horse battery\"}")
                        .body(),
                "access_token");
        var family = UUID.fromString(decoder.decode(access).getClaimAsString("sid"));
        var parts = access.split("\\.");
        var signature = parts[2].charAt(0) == 'A' ? "B" : "A";
        assertThat(logout(parts[0] + "." + parts[1] + "." + signature + parts[2].substring(1))
                        .statusCode())
                .isEqualTo(401);
        var claims = new java.util.HashMap<>(decoder.decode(access).getClaims());
        var now = java.time.Instant.now();
        claims.put("iat", now.minusSeconds(180));
        claims.put("nbf", now.minusSeconds(180));
        claims.put("exp", now.minusSeconds(120));
        var expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(
                                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                                .keyId("test-key")
                                .type("JWT")
                                .build(),
                        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                                .claims(values -> values.putAll(claims))
                                .build()))
                .getTokenValue();
        assertThat(logout(expired).statusCode()).isEqualTo(401);
        try (var client = HttpClient.newHttpClient()) {
            var get = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/logout"))
                    .header("Authorization", "Bearer " + access)
                    .GET()
                    .build();
            assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .isIn(403, 405);
        }
        jdbc.update("update users set status = 'INACTIVE' where id = ?", user);
        assertThat(logout(access).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject(
                        "select revoked_at is null from session_families where id = ?", Boolean.class, family))
                .isTrue();
    }

    @Test
    void unexpectedLogoutFailureReturnsSanitized500() throws Exception {
        request(
                "/api/v1/auth/register",
                "{\"username\":\"logouterror\",\"email\":\"logouterror@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var token = value(
                login("{\"identifier\":\"logouterror\",\"password\":\"correct horse battery\"}")
                        .body(),
                "access_token");
        var family = UUID.fromString(decoder.decode(token).getClaimAsString("sid"));
        doThrow(new IllegalStateException("sensitive failure")).when(sessions).revoke(any(), any());
        var response = logout(token);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain(token, family.toString(), "sensitive");
        assertThat(jdbc.queryForObject(
                        "select revoked_at is null from session_families where id = ?", Boolean.class, family))
                .isTrue();
    }

    @Test
    void deferredCommitFailureDoesNotReturnLogoutSuccess() throws Exception {
        request(
                "/api/v1/auth/register",
                "{\"username\":\"logoutcommit\",\"email\":\"logoutcommit@example.com\",\"password\":\"correct horse battery\"}",
                "application/json");
        var token = value(
                login("{\"identifier\":\"logoutcommit\",\"password\":\"correct horse battery\"}")
                        .body(),
                "access_token");
        var family = UUID.fromString(decoder.decode(token).getClaimAsString("sid"));
        jdbc.execute(
                "create function test_http_logout_commit_failure() returns trigger language plpgsql as 'begin raise exception ''injected failure''; end'");
        try {
            jdbc.execute(
                    "create constraint trigger test_http_logout_commit_failure after update on session_families deferrable initially deferred for each row execute function test_http_logout_commit_failure()");
            var response = logout(token);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).doesNotContain(token, family.toString(), "injected", "SQL");
            assertThat(jdbc.queryForObject(
                            "select revoked_at is null from session_families where id = ?", Boolean.class, family))
                    .isTrue();
        } finally {
            jdbc.execute("drop trigger if exists test_http_logout_commit_failure on session_families");
            jdbc.execute("drop function test_http_logout_commit_failure()");
        }
        assertThat(logout(token).statusCode()).isEqualTo(204);
    }
}
