package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.SecurityStatePort;
import com.secureflow.identity.application.SecurityStateSnapshot;
import com.secureflow.identity.application.SecurityStateUnavailableException;
import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SecurityStatePersistenceAdapter implements SecurityStatePort {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityStatePersistenceAdapter.class);
    private final JdbcTemplate jdbc;

    SecurityStatePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SecurityStateSnapshot findCurrentState(UserId userId, UUID sessionFamilyId) {
        try {
            var snapshot = jdbc.queryForObject(
                    """
                    select u.id is not null, u.status = 'ACTIVE', u.security_version,
                           s.id is not null, s.user_id, s.created_at, s.expires_at, s.revoked_at is not null,
                           c.version
                    from (values (1)) anchor(id)
                    left join users u on u.id = ?
                    left join session_families s on s.id = ?
                    left join authorization_catalog_state c on c.singleton = true
                    """,
                    (rs, row) -> new SecurityStateSnapshot(
                            rs.getBoolean(1),
                            rs.getBoolean(2),
                            rs.getLong(3),
                            rs.getBoolean(4),
                            rs.getObject(5, UUID.class),
                            rs.getTimestamp(6) == null
                                    ? null
                                    : rs.getTimestamp(6).toInstant(),
                            rs.getTimestamp(7) == null
                                    ? null
                                    : rs.getTimestamp(7).toInstant(),
                            rs.getBoolean(8),
                            rs.getLong(9)),
                    userId.value(),
                    sessionFamilyId);
            if (snapshot == null || snapshot.catalogVersion() != JwtAuthorityCatalog.VERSION) {
                LOG.error("Authorization catalog state missing or incompatible with this application");
                throw new SecurityStateUnavailableException();
            }
            return snapshot;
        } catch (DataAccessException ex) {
            LOG.error("Security state database query unavailable");
            throw new SecurityStateUnavailableException();
        }
    }
}
