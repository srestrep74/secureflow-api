package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.*;
import com.secureflow.shared.domain.UserId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class RefreshConfiguration {
    @Bean
    RefreshTransactionPort refreshTransactionPort(PlatformTransactionManager manager, JdbcTemplate jdbc) {
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return (digest, operation) -> tx.execute(status -> {
            var locations = jdbc.query(
                    "select f.user_id, f.id from refresh_tokens t join session_families f on f.id = t.family_id where t.token_digest = ?",
                    (rs, row) -> new Location(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                    digest);
            if (locations.isEmpty()) return RefreshOutcome.invalid();
            var location = locations.getFirst();
            var users = jdbc.query(
                    "select id from users where id = ? for update",
                    (rs, row) -> rs.getObject(1, UUID.class),
                    location.userId());
            if (users.isEmpty()) return RefreshOutcome.invalid();
            var families = jdbc.query(
                    "select id from session_families where id = ? and user_id = ? for update",
                    (rs, row) -> rs.getObject(1, UUID.class),
                    location.familyId(),
                    location.userId());
            if (families.isEmpty()) return RefreshOutcome.invalid();
            return operation.apply(new UserId(location.userId()), location.familyId());
        });
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(
            UserRepositoryPort users,
            SessionRepositoryPort sessions,
            SecurityStatePort security,
            LoginTokenPort tokens,
            RefreshTransactionPort transaction) {
        return new RefreshSessionUseCase(users, sessions, security, tokens, transaction, Clock.systemUTC());
    }

    private record Location(UUID userId, UUID familyId) {}
}
