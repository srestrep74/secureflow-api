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

    @Autowired
    RefreshSessionUseCase refresh;

    @Autowired
    RefreshTransactionPort refreshTransaction;

    @Autowired
    SecurityStatePort securityState;

    @Test
    void rotatesAndCommitsReplayRevocation() {
        var user = register.register(
                new RegisterUserCommand("refreshuser", "refreshuser@example.com", "correct horse battery"));
        var original = login.login(new LoginCommand("refreshuser", "correct horse battery"));
        var oldJwt = decoder.decode(original.accessToken());
        var sid = UUID.fromString(oldJwt.getClaimAsString("sid"));
        var expiry = sessions.findFamily(sid).orElseThrow().expiresAt();
        jdbc.update(
                "insert into user_roles(user_id, role) values (?, 'AUDITOR')",
                user.userId().value());
        jdbc.update(
                "update users set security_version = security_version + 1 where id = ?",
                user.userId().value());
        var command = new RefreshSessionCommand(original.refreshToken());
        var rotated = refresh.refresh(command);
        var jwt = decoder.decode(rotated.accessToken());
        assertThat(command.toString()).doesNotContain(original.refreshToken());
        assertThat(rotated.toString()).doesNotContain(rotated.refreshToken(), rotated.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo(user.userId().value().toString());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sid.toString());
        assertThat(jwt.getClaimAsStringList("roles")).contains("ROLE_AUDITOR");
        assertThat(jwt.getClaimAsStringList("permissions")).contains("task:read:any");
        assertThat(((Number) jwt.getClaims().get("security_version")).longValue())
                .isEqualTo(1);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(sessions.findFamily(sid).orElseThrow().expiresAt()).isEqualTo(expiry);
        assertThat(sessions.findTokenByDigest(secrets.digest(original.refreshToken()))
                        .orElseThrow()
                        .consumedAt())
                .isNotNull();
        assertThat(sessions.findTokenByDigest(secrets.digest(rotated.refreshToken())))
                .isPresent();
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ? and consumed_at is null",
                        Integer.class,
                        sid))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where token_digest = ?",
                        Integer.class,
                        rotated.refreshToken()))
                .isZero();
        assertThatThrownBy(() -> refresh.refresh(command)).isInstanceOf(RefreshReplayException.class);
        assertThat(sessions.findFamily(sid).orElseThrow().revokedAt()).isNotNull();
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(rotated.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refresh.refresh(command)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(securityState.findCurrentState(user.userId(), sid).sessionRevoked())
                .isTrue();
    }

    @Test
    void rejectsInvalidAndUnavailableState() {
        var user = register.register(
                new RegisterUserCommand("refreshinvalid", "refreshinvalid@example.com", "correct horse battery"));
        var initial = login.login(new LoginCommand("refreshinvalid", "correct horse battery"));
        var sid = UUID.fromString(decoder.decode(initial.accessToken()).getClaimAsString("sid"));
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand("bad")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(secrets.generate())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        jdbc.update(
                "update users set status = 'INACTIVE' where id = ?",
                user.userId().value());
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        jdbc.update(
                "update users set status = 'ACTIVE' where id = ?", user.userId().value());
        jdbc.update("update authorization_catalog_state set version = 2 where singleton = true");
        try {
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                    .isInstanceOf(RefreshUnavailableException.class);
        } finally {
            jdbc.update("update authorization_catalog_state set version = 1 where singleton = true");
        }
        jdbc.update(
                "update refresh_tokens set expires_at = issued_at + interval '1 second', issued_at = issued_at - interval '2 seconds' where family_id = ?",
                sid);
        jdbc.update("update refresh_tokens set expires_at = issued_at + interval '1 second' where family_id = ?", sid);
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(sessions.findFamily(sid).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void rollsBackInsertionAndSigningFailures() {
        register.register(
                new RegisterUserCommand("refreshrollback", "refreshrollback@example.com", "correct horse battery"));
        var initial = login.login(new LoginCommand("refreshrollback", "correct horse battery"));
        var sid = UUID.fromString(decoder.decode(initial.accessToken()).getClaimAsString("sid"));
        var originalDigest = secrets.digest(initial.refreshToken());
        LoginTokenPort duplicate = wrappedTokens(originalDigest, false);
        var failingInsert = new RefreshSessionUseCase(
                users, sessions, securityState, duplicate, refreshTransaction, Clock.systemUTC());
        assertThatThrownBy(() -> failingInsert.refresh(new RefreshSessionCommand(initial.refreshToken())))
                .isInstanceOf(RefreshUnavailableException.class);
        var failingSign = new RefreshSessionUseCase(
                users, sessions, securityState, wrappedTokens(null, true), refreshTransaction, Clock.systemUTC());
        assertThatThrownBy(() -> failingSign.refresh(new RefreshSessionCommand(initial.refreshToken())))
                .isInstanceOf(RefreshUnavailableException.class);
        assertThat(sessions.findTokenByDigest(originalDigest).orElseThrow().consumedAt())
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where family_id = ?", Integer.class, sid))
                .isEqualTo(1);
        assertThat(refresh.refresh(new RefreshSessionCommand(initial.refreshToken()))
                        .accessToken())
                .isNotBlank();
    }

    @Test
    void rejectsExpiredAndRevokedFamiliesAndDoesNotRestoreRemovedRoles() {
        var user = register.register(
                new RegisterUserCommand("refreshroles", "refreshroles@example.com", "correct horse battery"));
        jdbc.update(
                "insert into user_roles(user_id,role) values (?, 'AUDITOR')",
                user.userId().value());
        jdbc.update(
                "update users set security_version = security_version + 1 where id = ?",
                user.userId().value());
        var original = login.login(new LoginCommand("refreshroles", "correct horse battery"));
        jdbc.update(
                "delete from user_roles where user_id = ? and role = 'AUDITOR'",
                user.userId().value());
        jdbc.update(
                "update users set security_version = security_version + 1 where id = ?",
                user.userId().value());
        var renewed = refresh.refresh(new RefreshSessionCommand(original.refreshToken()));
        var jwt = decoder.decode(renewed.accessToken());
        assertThat(jwt.getClaimAsStringList("roles")).doesNotContain("ROLE_AUDITOR");
        assertThat(jwt.getClaimAsStringList("permissions")).doesNotContain("task:read:any");
        assertThat(((Number) jwt.getClaims().get("security_version")).longValue())
                .isEqualTo(2);
        var sid = UUID.fromString(jwt.getClaimAsString("sid"));
        jdbc.update("update session_families set revoked_at = created_at where id = ?", sid);
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(renewed.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        var expired = login.login(new LoginCommand("refreshroles", "correct horse battery"));
        var expiredId = UUID.fromString(decoder.decode(expired.accessToken()).getClaimAsString("sid"));
        jdbc.update(
                "update session_families set created_at = created_at - interval '31 days', expires_at = expires_at - interval '31 days' where id = ?",
                expiredId);
        assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(expired.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(sessions.findFamily(expiredId).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void commitFailuresDoNotReturnTokensOrClaimReplayRevocation() {
        register.register(
                new RegisterUserCommand("refreshcommit", "refreshcommit@example.com", "correct horse battery"));
        var initial = login.login(new LoginCommand("refreshcommit", "correct horse battery"));
        var sid = UUID.fromString(decoder.decode(initial.accessToken()).getClaimAsString("sid"));
        jdbc.execute(
                "create function test_refresh_commit_failure() returns trigger language plpgsql as 'begin raise exception ''injected commit failure''; end'");
        try {
            jdbc.execute(
                    "create trigger test_refresh_commit_failure before update on refresh_tokens for each row execute function test_refresh_commit_failure()");
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                    .isInstanceOf(RefreshUnavailableException.class);
            assertThat(sessions.findTokenByDigest(secrets.digest(initial.refreshToken()))
                            .orElseThrow()
                            .consumedAt())
                    .isNull();
            jdbc.execute("drop trigger test_refresh_commit_failure on refresh_tokens");
            jdbc.execute(
                    "create constraint trigger test_refresh_commit_failure after insert on refresh_tokens deferrable initially deferred for each row execute function test_refresh_commit_failure()");
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                    .isInstanceOf(RefreshUnavailableException.class);
            assertThat(sessions.findTokenByDigest(secrets.digest(initial.refreshToken()))
                            .orElseThrow()
                            .consumedAt())
                    .isNull();
            assertThat(jdbc.queryForObject(
                            "select count(*) from refresh_tokens where family_id = ?", Integer.class, sid))
                    .isEqualTo(1);
            jdbc.execute("drop trigger test_refresh_commit_failure on refresh_tokens");
            var successor = refresh.refresh(new RefreshSessionCommand(initial.refreshToken()));
            jdbc.execute(
                    "create constraint trigger test_refresh_commit_failure after update on session_families deferrable initially deferred for each row execute function test_refresh_commit_failure()");
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                    .isInstanceOf(RefreshUnavailableException.class);
            assertThat(sessions.findFamily(sid).orElseThrow().revokedAt()).isNull();
            jdbc.execute("drop trigger test_refresh_commit_failure on session_families");
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken())))
                    .isInstanceOf(RefreshReplayException.class);
            assertThat(sessions.findFamily(sid).orElseThrow().revokedAt()).isNotNull();
            assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(successor.refreshToken())))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        } finally {
            jdbc.execute("drop trigger if exists test_refresh_commit_failure on refresh_tokens");
            jdbc.execute("drop trigger if exists test_refresh_commit_failure on session_families");
            jdbc.execute("drop function test_refresh_commit_failure()");
        }
    }

    @Test
    void serializesConcurrentRefreshAndRevokesOnReplay() throws Exception {
        register.register(new RegisterUserCommand("refreshrace", "refreshrace@example.com", "correct horse battery"));
        var initial = login.login(new LoginCommand("refreshrace", "correct horse battery"));
        var sid = UUID.fromString(decoder.decode(initial.accessToken()).getClaimAsString("sid"));
        var signing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        LoginTokenPort held = new LoginTokenPort() {
            public String generateRefreshToken() {
                return tokens.generateRefreshToken();
            }

            public String digestRefreshToken(String value) {
                return tokens.digestRefreshToken(value);
            }

            public String issueAccessToken(User user, SessionFamily family) {
                signing.countDown();
                try {
                    if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return tokens.issueAccessToken(user, family);
            }
        };
        var firstUseCase =
                new RefreshSessionUseCase(users, sessions, securityState, held, refreshTransaction, Clock.systemUTC());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstUseCase.refresh(new RefreshSessionCommand(initial.refreshToken())));
            try {
                assertThat(signing.await(20, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
                var second = executor.submit(
                        () -> catchThrowable(() -> refresh.refresh(new RefreshSessionCommand(initial.refreshToken()))));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                boolean blocked = false;
                while (System.nanoTime() < deadline && !blocked) {
                    blocked = jdbc.queryForObject(
                            "select exists(select 1 from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like 'select id from users where id =%')",
                            Boolean.class);
                    Thread.onSpinWait();
                }
                assertThat(blocked).isTrue();
                release.countDown();
                var result = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(RefreshReplayException.class);
                assertThat(sessions.findFamily(sid).orElseThrow().revokedAt()).isNotNull();
                assertThatThrownBy(() -> refresh.refresh(new RefreshSessionCommand(result.refreshToken())))
                        .isInstanceOf(InvalidRefreshTokenException.class);
                assertThat(jdbc.queryForObject(
                                "select count(*) from refresh_tokens where family_id = ?", Integer.class, sid))
                        .isEqualTo(2);
            } finally {
                release.countDown();
            }
        }
    }

    LoginTokenPort wrappedTokens(String successorDigest, boolean failSigning) {
        return new LoginTokenPort() {
            int calls;

            public String generateRefreshToken() {
                return tokens.generateRefreshToken();
            }

            public String digestRefreshToken(String value) {
                calls++;
                return calls == 2 && successorDigest != null ? successorDigest : tokens.digestRefreshToken(value);
            }

            public String issueAccessToken(User user, SessionFamily family) {
                if (failSigning) throw new IllegalStateException("signing failed");
                return tokens.issueAccessToken(user, family);
            }
        };
    }

    int count(UUID id) {
        return jdbc.queryForObject("select count(*) from session_families where user_id = ?", Integer.class, id);
    }
}
