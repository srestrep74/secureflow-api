package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.CredentialRepositoryPort;
import com.secureflow.shared.domain.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CredentialPersistenceAdapter implements CredentialRepositoryPort {
    private final JdbcTemplate jdbc;

    CredentialPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void create(UserId userId, String passwordHash) {
        Objects.requireNonNull(userId);
        if (passwordHash == null
                || !passwordHash.matches(
                        "\\$argon2id\\$v=19\\$m=[0-9]+,t=[0-9]+,p=[0-9]+\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+")) {
            throw new IllegalArgumentException("Invalid password hash format");
        }
        jdbc.update(
                "insert into user_credentials (user_id, password_hash) values (?, ?)", userId.value(), passwordHash);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findHash(UserId userId) {
        return jdbc
                .query(
                        "select password_hash from user_credentials where user_id = ?",
                        (rs, row) -> rs.getString(1),
                        userId.value())
                .stream()
                .findFirst();
    }
}
