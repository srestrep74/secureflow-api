package com.secureflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationHttpIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void registersThroughHttp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    "{\"username\":\"httpuser\",\"email\":\"http@example.com\",\"password\":\"a valid test password\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).doesNotContain("a valid test password", "argon2");
            var hash = jdbc.queryForObject(
                    "select c.password_hash from user_credentials c join users u on u.id = c.user_id where u.username = 'httpuser'",
                    String.class);
            assertThat(hash).startsWith("$argon2id$");
            assertThat(encoder.matches("a valid test password", hash)).isTrue();
            assertThat(jdbc.queryForObject(
                            "select r.role from user_roles r join users u on u.id = r.user_id where u.username = 'httpuser'",
                            String.class))
                    .isEqualTo("USER");
            assertThat(post(
                                    client,
                                    "{\"username\":\"HTTPUSER\",\"email\":\"other@example.test\",\"password\":\"a valid test password\"}")
                            .statusCode())
                    .isEqualTo(409);
            assertThat(post(
                                    client,
                                    "{\"username\":\"other\",\"email\":\"HTTP@example.com\",\"password\":\"a valid test password\"}")
                            .statusCode())
                    .isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from users where username = 'other'", Integer.class))
                    .isZero();
        }
    }

    @Test
    void rejectsInvalidRequestsAndDeniesUnrelatedRoutes() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (var body : new String[] {
                "{}",
                "{\"email\":\"a@example.test\",\"password\":\"a valid test password\"}",
                "{\"username\":\"valid\",\"password\":\"a valid test password\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\"}",
                "{\"username\":null,\"email\":\"a@example.test\",\"password\":\"a valid test password\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"valid password \\ud800\"}",
                "{\"username\":\"!\",\"email\":\"a@example.test\",\"password\":\"a valid test password\"}",
                "{\"username\":\"valid\",\"email\":\"bad\",\"password\":\"a valid test password\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"short\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"" + "a".repeat(129) + "\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"" + "ðŸ˜€".repeat(129) + "\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"" + "a".repeat(5000) + "\"}",
                "{\"username\":\"valid\",\"email\":\"a@example.test\",\"password\":\"a valid test password\",\"role\":\"ADMIN\"}",
                "{"
            }) {
                var response = post(client, body);
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).doesNotContain("a valid test password", "short");
            }
            for (var path : new String[] {"/api/v1/auth/register", "/api/v1/tasks", "/actuator/env", "/v3/api-docs"}) {
                var response = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isIn(401, 403, 404, 405);
            }
        }
    }

    @Test
    void unexpectedCredentialConstraintFailureIsNotAConflictAndRollsBack() throws Exception {
        jdbc.execute(
                "alter table user_credentials add constraint test_http_reject_credentials check (false) not valid");
        try (var client = HttpClient.newHttpClient()) {
            var response = post(
                    client,
                    "{\"username\":\"httpfailure\",\"email\":\"failure@example.test\",\"password\":\"a valid test password\"}");
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).doesNotContain("test_http_reject_credentials", "password", "sql");
            assertThat(jdbc.queryForObject("select count(*) from users where username = 'httpfailure'", Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("alter table user_credentials drop constraint test_http_reject_credentials");
        }
    }

    @Test
    void disabledJwtDeniesLoginWithoutCreatingSessions() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\"nobody\",\"password\":\"secret\"}"))
                    .build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString())
                            .statusCode())
                    .isIn(401, 403, 404);
            assertThat(jdbc.queryForObject("select count(*) from session_families", Integer.class))
                    .isZero();
        }
    }

    @Test
    void bootstrapIsNotPubliclyAccessible() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/bootstrap"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"userId\":\"00000000-0000-0000-0000-000000000001\"}"))
                    .build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString())
                            .statusCode())
                    .isIn(401, 403, 404);
            assertThat(jdbc.queryForObject("select count(*) from user_roles where role = 'ADMIN'", Integer.class))
                    .isZero();
        }
    }

    private HttpResponse<String> post(HttpClient client, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
