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
            assertThat(docs.body()).contains("/api/v1/auth/login", "access_token", "refresh_token");
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
}
