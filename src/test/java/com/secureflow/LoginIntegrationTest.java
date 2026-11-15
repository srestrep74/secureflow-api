package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.*;
import com.secureflow.identity.domain.User;
import com.secureflow.identity.infrastructure.RefreshTokenSecrets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class LoginIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static Path privateKey;
    static Path publicKey;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keys = generator.generateKeyPair();
            var dir = Files.createTempDirectory("secureflow-test-keys");
            privateKey = write(
                    dir.resolve("private.pem"), "PRIVATE KEY", keys.getPrivate().getEncoded());
            publicKey = write(
                    dir.resolve("public.pem"), "PUBLIC KEY", keys.getPublic().getEncoded());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Path write(Path path, String label, byte[] key) throws Exception {
        return Files.writeString(
                path,
                "-----BEGIN " + label + "-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key) + "\n-----END " + label
                        + "-----\n");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("secureflow.jwt.enabled", () -> true);
        registry.add("secureflow.jwt.private-key", () -> privateKey.toString());
        registry.add("secureflow.jwt.public-key", () -> publicKey.toString());
        registry.add("secureflow.jwt.key-id", () -> "test-key");
        registry.add("secureflow.jwt.issuer", () -> "https://issuer.example");
        registry.add("secureflow.jwt.audience", () -> "secureflow");
    }

    @Autowired
    RegisterUserUseCase register;

    @Autowired
    LoginUseCase login;

    @Autowired
    AuthenticateCredentialsUseCase authenticate;

    @Autowired
    UserRepositoryPort users;

    @Autowired
    SessionRepositoryPort sessions;

    @Autowired
    LoginTokenPort tokens;

    @Autowired
    LoginTransactionPort transaction;

    @Autowired
    RefreshTokenSecrets secrets;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void issuesSignedTokensForCommittedSession() {
        var user = register.register(
                new RegisterUserCommand("loginsuccess", "loginsuccess@example.com", "correct horse battery"));
        jdbc.update(
                "insert into user_roles(user_id,role) values (?, 'AUDITOR')",
                user.userId().value());
        jdbc.update(
                "update users set security_version = 1 where id = ?",
                user.userId().value());
        var command = new LoginCommand("loginsuccess", "correct horse battery");
        var result = login.login(command);
        var jwt = decoder.decode(result.accessToken());
        var sid = UUID.fromString(jwt.getClaimAsString("sid"));
        var family = sessions.findFamily(sid).orElseThrow();
        var token = sessions.findTokenByDigest(secrets.digest(result.refreshToken()))
                .orElseThrow();
        assertThat(token.familyId()).isEqualTo(sid);
        assertThat(family.userId()).isEqualTo(user.userId());
        assertThat(result.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject(
                        "select token_digest from refresh_tokens where family_id = ?", String.class, sid))
                .isEqualTo(secrets.digest(result.refreshToken()))
                .isNotEqualTo(result.refreshToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo(user.userId().value().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("ROLE_USER", "ROLE_AUDITOR");
        assertThat(jwt.getClaimAsStringList("permissions")).contains("task:read:own", "task:read:any");
        assertThat(((Number) jwt.getClaims().get("security_version")).longValue())
                .isEqualTo(1);
        assertThat(((Number) jwt.getClaims().get("catalog_version")).longValue())
                .isEqualTo(1);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(command.toString()).doesNotContain("correct horse battery");
        assertThat(result.toString()).doesNotContain(result.accessToken(), result.refreshToken());
    }

    @Test
    void rejectsCredentialsWithoutCreatingSessions() {
        var user = register.register(
                new RegisterUserCommand("loginfail", "loginfail@example.com", "correct horse battery"));
        var bad = catchThrowable(() -> login.login(new LoginCommand("loginfail", "wrong")));
        var unknown = catchThrowable(() -> login.login(new LoginCommand("unknown", "wrong")));
        assertThat(bad).isInstanceOf(InvalidCredentialsException.class).hasMessage(unknown.getMessage());
        assertThatThrownBy(() -> login.login(new LoginCommand(null, null)))
                .isInstanceOf(InvalidCredentialsException.class);
        jdbc.update(
                "update users set status = 'INACTIVE' where id = ?",
                user.userId().value());
        assertThatThrownBy(() -> login.login(new LoginCommand("loginfail", "correct horse battery")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(count(user.userId().value())).isZero();
    }

    @Test
    void rollsBackSigningAndPersistenceFailuresAndRechecksStatus() {
        var user = register.register(
                new RegisterUserCommand("loginrollback", "loginrollback@example.com", "correct horse battery"));
        LoginTokenPort failing = new LoginTokenPort() {
            public String generateRefreshToken() {
                return tokens.generateRefreshToken();
            }

            public String digestRefreshToken(String token) {
                return tokens.digestRefreshToken(token);
            }

            public String issueAccessToken(User u, SessionFamily f) {
                throw new IllegalStateException("Signing unavailable");
            }
        };
        var signFailure = new LoginUseCase(authenticate, users, sessions, failing, transaction, Clock.systemUTC());
        assertThatThrownBy(() -> signFailure.login(new LoginCommand("loginrollback", "correct horse battery")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Signing unavailable");
        assertThat(count(user.userId().value())).isZero();
        LoginTokenPort duplicate = new LoginTokenPort() {
            public String generateRefreshToken() {
                return tokens.generateRefreshToken();
            }

            public String digestRefreshToken(String token) {
                return "0".repeat(64);
            }

            public String issueAccessToken(User u, SessionFamily f) {
                return tokens.issueAccessToken(u, f);
            }
        };
        var duplicateLogin = new LoginUseCase(authenticate, users, sessions, duplicate, transaction, Clock.systemUTC());
        duplicateLogin.login(new LoginCommand("loginrollback", "correct horse battery"));
        assertThatThrownBy(() -> duplicateLogin.login(new LoginCommand("loginrollback", "correct horse battery")))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(InvalidCredentialsException.class);
        assertThat(count(user.userId().value())).isEqualTo(1);
        var stale = new AuthenticateCredentialsUseCase((identifier, password) -> {
            var identity = authenticate.authenticate(new AuthenticateCredentialsCommand(identifier, password));
            jdbc.update(
                    "update users set status = 'INACTIVE', security_version = security_version + 1 where id = ?",
                    identity.userId().value());
            return identity;
        });
        var staleLogin = new LoginUseCase(stale, users, sessions, tokens, transaction, Clock.systemUTC());
        assertThatThrownBy(() -> staleLogin.login(new LoginCommand("loginrollback", "correct horse battery")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(count(user.userId().value())).isEqualTo(1);
    }

    int count(UUID id) {
        return jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, id);
    }
}
