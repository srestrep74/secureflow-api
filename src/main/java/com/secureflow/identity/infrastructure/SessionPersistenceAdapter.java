package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.RefreshTokenRecord;
import com.secureflow.identity.application.SessionFamily;
import com.secureflow.identity.application.SessionRepositoryPort;
import com.secureflow.shared.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SessionPersistenceAdapter implements SessionRepositoryPort {
    private final JdbcTemplate jdbc;

    SessionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void create(SessionFamily family, RefreshTokenRecord token) {
        if (!family.id().equals(token.familyId())
                || token.consumedAt() != null
                || token.issuedAt().isBefore(family.createdAt())
                || token.expiresAt().isAfter(family.expiresAt())
                || family.revokedAt() != null) throw new IllegalArgumentException("Invalid initial token");
        jdbc.update(
                "insert into session_families(id,user_id,created_at,expires_at) values (?,?,?,?)",
                family.id(),
                family.userId().value(),
                Timestamp.from(family.createdAt()),
                Timestamp.from(family.expiresAt()));
        jdbc.update(
                "insert into refresh_tokens(id,family_id,token_digest,issued_at,expires_at) values (?,?,?,?,?)",
                token.id(),
                token.familyId(),
                token.digest(),
                Timestamp.from(token.issuedAt()),
                Timestamp.from(token.expiresAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionFamily> findFamily(UUID id) {
        return jdbc
                .query(
                        "select id,user_id,created_at,expires_at,revoked_at from session_families where id = ?",
                        (rs, n) -> new SessionFamily(
                                rs.getObject(1, UUID.class),
                                new UserId(rs.getObject(2, UUID.class)),
                                rs.getTimestamp(3).toInstant(),
                                rs.getTimestamp(4).toInstant(),
                                rs.getTimestamp(5) == null
                                        ? null
                                        : rs.getTimestamp(5).toInstant()),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenRecord> findTokenByDigest(String digest) {
        return jdbc
                .query(
                        "select id,family_id,token_digest,issued_at,expires_at,consumed_at from refresh_tokens where token_digest = ?",
                        (rs, n) -> new RefreshTokenRecord(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getTimestamp(4).toInstant(),
                                rs.getTimestamp(5).toInstant(),
                                rs.getTimestamp(6) == null
                                        ? null
                                        : rs.getTimestamp(6).toInstant()),
                        digest)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public boolean revoke(UUID id, Instant at) {
        return jdbc.update(
                        "update session_families set revoked_at = ? where id = ? and revoked_at is null and created_at <= ?",
                        Timestamp.from(at),
                        id,
                        Timestamp.from(at))
                == 1;
    }
}
