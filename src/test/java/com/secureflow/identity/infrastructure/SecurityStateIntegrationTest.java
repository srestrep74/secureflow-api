package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.*;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class SecurityStateIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RegisterUserUseCase register;

    @Autowired
    UserRepositoryPort users;

    @Autowired
    SessionRepositoryPort sessions;

    @Autowired
    SecurityStatePort state;

    @Autowired
    SecurityStateValidator validator;

    @Autowired
    RefreshTokenSecrets secrets;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    private UserId user() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return register.register(new RegisterUserCommand(name, name + "@example.com", "correct horse battery"))
                .userId();
    }

    private SessionFamily session(UserId user, Instant created) {
        var family = SessionFamily.create(user, created.truncatedTo(ChronoUnit.MICROS));
        sessions.create(
                family,
                new RefreshTokenRecord(
                        UUID.randomUUID(),
                        family.id(),
                        secrets.digest(secrets.generate()),
                        family.createdAt(),
                        family.expiresAt(),
                        null));
        return family;
    }

    private TrustedSecurityClaims claims(UserId user, UUID session, long version, long catalog) {
        return new TrustedSecurityClaims(user, session, version, catalog);
    }

    @Test
    void validatesCurrentStateAndCommittedMutations() {
        var user = user();
        var other = user();
        var family = session(user, Instant.now().minusSeconds(10));
        var otherFamily = session(other, Instant.now().minusSeconds(10));
        var good = claims(user, family.id(), 0, 1);
        var snapshot = state.findCurrentState(user, family.id());
        assertThat(snapshot.accountExists()).isTrue();
        assertThat(snapshot.accountActive()).isTrue();
        assertThat(snapshot.sessionExists()).isTrue();
        assertThat(snapshot.sessionOwnerId()).isEqualTo(user.value());
        assertThat(snapshot.securityVersion()).isZero();
        assertThat(snapshot.catalogVersion()).isEqualTo(JwtAuthorityCatalog.VERSION);
        assertThat(validator.isValid(good)).isTrue();
        assertThat(validator.isValid(claims(new UserId(UUID.randomUUID()), family.id(), 0, 1)))
                .isFalse();
        assertThat(validator.isValid(claims(user, UUID.randomUUID(), 0, 1))).isFalse();
        assertThat(validator.isValid(claims(user, otherFamily.id(), 0, 1))).isFalse();
        assertThat(validator.isValid(claims(user, family.id(), 1, 1))).isFalse();
        assertThat(validator.isValid(claims(user, family.id(), 0, 2))).isFalse();
        var expired = session(user, Instant.now().minusSeconds(31L * 86400));
        assertThat(validator.isValid(claims(user, expired.id(), 0, 1))).isFalse();
        var aggregate = users.findById(user).orElseThrow();
        aggregate.assignRole(Role.AUDITOR);
        users.update(aggregate, 0);
        assertThat(validator.isValid(good)).isFalse();
        assertThat(validator.isValid(claims(other, otherFamily.id(), 0, 1))).isTrue();
        assertThat(validator.isValid(claims(user, family.id(), 1, 1))).isTrue();
        aggregate.changeStatus(AccountStatus.INACTIVE);
        users.update(aggregate, 1);
        assertThat(validator.isValid(claims(user, family.id(), 2, 1))).isFalse();
        aggregate.changeStatus(AccountStatus.ACTIVE);
        users.update(aggregate, 2);
        var current = claims(user, family.id(), 3, 1);
        assertThat(validator.isValid(current)).isTrue();
        assertThat(sessions.revoke(family.id(), Instant.now())).isTrue();
        assertThat(sessions.revoke(family.id(), Instant.now())).isFalse();
        assertThat(validator.isValid(current)).isFalse();
    }

    @Test
    void catalogIsSingletonPositiveAndIncompatibilityFailsClosed() {
        var user = user();
        var family = session(user, Instant.now().minusSeconds(1));
        assertThat(jdbc.queryForObject("select version from authorization_catalog_state", Long.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into authorization_catalog_state values (true, 1)"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("update authorization_catalog_state set version = 0"))
                .isInstanceOf(RuntimeException.class);
        try {
            jdbc.update("update authorization_catalog_state set version = 2");
            assertThatThrownBy(() -> validator.isValid(claims(user, family.id(), 0, 2)))
                    .isInstanceOf(SecurityStateUnavailableException.class);
            jdbc.update("delete from authorization_catalog_state");
            assertThatThrownBy(() -> validator.isValid(claims(user, family.id(), 0, 1)))
                    .isInstanceOf(SecurityStateUnavailableException.class);
        } finally {
            jdbc.update(
                    "insert into authorization_catalog_state values (true, 1) on conflict (singleton) do update set version = 1");
        }
    }

    @Test
    void unavailableConnectionIsNotInvalidCredentialsAndIsSanitized() {
        var ds = new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:1/unavailable?connectTimeout=1", "unused", "unused");
        var broken = new SecurityStatePersistenceAdapter(new JdbcTemplate(ds));
        assertThatThrownBy(() -> broken.findCurrentState(new UserId(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(SecurityStateUnavailableException.class)
                .hasMessage("Security state unavailable")
                .hasNoCause();
    }

    @Test
    void schemaHasPrimaryKeyIndexesForProjection() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname = current_schema() and indexname in ('users_pkey','session_families_pkey','authorization_catalog_state_pkey')",
                        Integer.class))
                .isEqualTo(3);
    }

    @Test
    void readsCommittedAccountAndSessionStateTogether() throws Exception {
        var user = user();
        var family = session(user, Instant.now().minusSeconds(10));
        var changed = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var aggregate = users.findById(user).orElseThrow();
                aggregate.changeStatus(AccountStatus.INACTIVE);
                users.update(aggregate, 0);
                changed.countDown();
                try {
                    if (!proceed.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Writer timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                sessions.revoke(family.id(), Instant.now());
            }));
            try {
                assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
                var before = state.findCurrentState(user, family.id());
                assertThat(before.accountActive()).isTrue();
                assertThat(before.sessionRevoked()).isFalse();
            } finally {
                proceed.countDown();
            }
            writer.get(10, TimeUnit.SECONDS);
            var after = state.findCurrentState(user, family.id());
            assertThat(after.accountActive()).isFalse();
            assertThat(after.securityVersion()).isEqualTo(1);
            assertThat(after.sessionRevoked()).isTrue();
        }
    }
}
