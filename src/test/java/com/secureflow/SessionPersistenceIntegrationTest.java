package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.*;
import com.secureflow.identity.infrastructure.RefreshTokenSecrets;
import com.secureflow.shared.domain.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class SessionPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    SessionRepositoryPort sessions;

    @Autowired
    RefreshTokenSecrets secrets;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RegisterUserUseCase register;

    @Test
    void persistsSecretDigestAndRevokesIdempotently() {
        var user = register.register(
                        new RegisterUserCommand("sessions", "sessions@example.com", "correct horse battery"))
                .userId();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var family = SessionFamily.create(user, now);
        var raw = secrets.generate();
        var digest = secrets.digest(raw);
        var token = new RefreshTokenRecord(UUID.randomUUID(), family.id(), digest, now, family.expiresAt(), null);
        sessions.create(family, token);
        assertThat(raw).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(secrets.generate());
        assertThat(secrets.digest(secrets.generate())).isNotEqualTo(digest);
        assertThat(sessions.findFamily(family.id())).contains(family);
        assertThat(sessions.findTokenByDigest(digest)).contains(token);
        assertThat(jdbc.queryForObject(
                        "select token_digest from refresh_tokens where id = ?", String.class, token.id()))
                .isEqualTo(digest)
                .isNotEqualTo(raw);
        assertThat(token.toString()).doesNotContain(digest, raw);
        assertThat(family.isActiveAt(now)).isTrue();
        assertThat(family.isActiveAt(family.expiresAt())).isFalse();
        assertThat(sessions.revoke(family.id(), now.plusSeconds(1))).isTrue();
        assertThat(sessions.revoke(family.id(), now.plusSeconds(2))).isFalse();
        assertThat(sessions.findFamily(family.id()).orElseThrow().revokedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(sessions.findFamily(family.id()).orElseThrow().isActiveAt(now.plusSeconds(2)))
                .isFalse();
    }

    @Test
    void rollbackAndConstraints() {
        UserId user = register.register(new RegisterUserCommand(
                        "rollbacksession", "rollbacksession@example.com", "correct horse battery"))
                .userId();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var first = SessionFamily.create(user, now);
        var digest = secrets.digest(secrets.generate());
        sessions.create(
                first, new RefreshTokenRecord(UUID.randomUUID(), first.id(), digest, now, first.expiresAt(), null));
        var second = SessionFamily.create(user, now);
        assertThatThrownBy(() -> sessions.create(
                        second,
                        new RefreshTokenRecord(UUID.randomUUID(), second.id(), digest, now, second.expiresAt(), null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(sessions.findFamily(second.id())).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ?", Integer.class, second.id()))
                .isZero();
        sessions.create(
                second,
                new RefreshTokenRecord(
                        UUID.randomUUID(),
                        second.id(),
                        secrets.digest(secrets.generate()),
                        now,
                        second.expiresAt(),
                        null));
        assertThat(sessions.findFamily(second.id())).isPresent();
        var nonexistent = SessionFamily.create(new UserId(UUID.randomUUID()), now);
        assertThatThrownBy(() -> sessions.create(
                        nonexistent,
                        new RefreshTokenRecord(
                                UUID.randomUUID(),
                                nonexistent.id(),
                                secrets.digest(secrets.generate()),
                                now,
                                nonexistent.expiresAt(),
                                null)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new SessionFamily(UUID.randomUUID(), user, now, now, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into refresh_tokens(id,family_id,token_digest,issued_at,expires_at) values (?,?,?,?,?)",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        secrets.digest(secrets.generate()),
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void databaseConstraintsAndTokenStates() {
        var user = register.register(
                        new RegisterUserCommand("constraintuser", "constraint@example.com", "correct horse battery"))
                .userId();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var family = SessionFamily.create(user, now);
        var digest = secrets.digest(secrets.generate());
        var token = new RefreshTokenRecord(UUID.randomUUID(), family.id(), digest, now, family.expiresAt(), null);
        sessions.create(family, token);
        assertThat(token.isUsableAt(now, family)).isTrue();
        assertThat(token.isUsableAt(family.expiresAt(), family)).isFalse();
        assertThat(sessions.findTokenByDigest(secrets.digest(secrets.generate())))
                .isEmpty();
        assertThatThrownBy(() -> jdbc.update(
                        "insert into refresh_tokens(id,family_id,token_digest,issued_at,expires_at) values (?,?,?,?,?)",
                        UUID.randomUUID(),
                        family.id(),
                        secrets.digest(secrets.generate()),
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into session_families(id,user_id,created_at,expires_at) values (?,?,?,?)",
                        UUID.randomUUID(),
                        user.value(),
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "update refresh_tokens set consumed_at = ? where id = ?",
                        java.sql.Timestamp.from(now.minusSeconds(1)),
                        token.id()))
                .isInstanceOf(RuntimeException.class);
        jdbc.update(
                "update refresh_tokens set consumed_at = ? where id = ?",
                java.sql.Timestamp.from(now.plusSeconds(1)),
                token.id());
        var consumed = sessions.findTokenByDigest(digest).orElseThrow();
        assertThat(consumed.consumedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(consumed.isUsableAt(now.plusSeconds(2), family)).isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname = current_schema() and indexname in ('session_families_user_id_idx', 'refresh_tokens_one_unconsumed_per_family_idx')",
                        Integer.class))
                .isEqualTo(2);
    }
}
