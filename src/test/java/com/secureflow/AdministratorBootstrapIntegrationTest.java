package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.AdministratorBootstrap;
import com.secureflow.identity.application.RegisterUserCommand;
import com.secureflow.identity.application.RegisterUserUseCase;
import com.secureflow.identity.infrastructure.AdministratorBootstrapService;
import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
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
class AdministratorBootstrapIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RegisterUserUseCase register;

    @Autowired
    AdministratorBootstrapService bootstrap;

    @Autowired
    com.secureflow.identity.application.AdministratorGuardPort guard;

    @Autowired
    org.springframework.transaction.support.TransactionTemplate transactions;

    @BeforeEach
    void clean() {
        jdbc.update("delete from user_credentials");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
    }

    UserId user(String name) {
        return register.register(new RegisterUserCommand(name, name + "@example.com", "correct horse battery"))
                .userId();
    }

    long countAdmins() {
        return jdbc.queryForObject("select count(*) from user_roles where role = 'ADMIN'", Long.class);
    }

    @Test
    void promotesAndRepeatsWithoutAnotherWrite() {
        var id = user("firstuser");
        assertThat(countAdmins()).isZero();
        assertThat(bootstrap.execute(id)).isEqualTo(AdministratorBootstrap.Outcome.PROMOTED);
        var before =
                jdbc.queryForMap("select status, security_version, lock_version from users where id = ?", id.value());
        assertThat(before.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) before.get("security_version")).longValue()).isEqualTo(1);
        assertThat(jdbc.queryForList("select role from user_roles where user_id = ?", String.class, id.value()))
                .containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(bootstrap.execute(id)).isEqualTo(AdministratorBootstrap.Outcome.ALREADY_ADMINISTRATOR);
        assertThat(jdbc.queryForMap(
                        "select status, security_version, lock_version from users where id = ?", id.value()))
                .isEqualTo(before);
        assertThat(countAdmins()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownInactiveMissingCredentialsAndAdditionalAdministrator() {
        var inactive = user("inactive");
        jdbc.update("update users set status = 'INACTIVE' where id = ?", inactive.value());
        var noCredential = user("nocredential");
        jdbc.update("delete from user_credentials where user_id = ?", noCredential.value());
        assertThatThrownBy(() -> bootstrap.execute(new UserId(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap.execute(inactive)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap.execute(noCredential)).isInstanceOf(IllegalStateException.class);
        assertThat(countAdmins()).isZero();
        var first = user("firstadmin");
        var second = user("seconduser");
        bootstrap.execute(first);
        assertThatThrownBy(() -> bootstrap.execute(second)).isInstanceOf(IllegalStateException.class);
        assertThat(countAdmins()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select security_version from users where id = ?", Long.class, second.value()))
                .isZero();
    }

    @Test
    void rollsBackWhenRolePersistenceFails() {
        var id = user("rollbackuser");
        jdbc.execute("alter table user_roles add constraint test_no_admin check (role <> 'ADMIN') not valid");
        try {
            assertThatThrownBy(() -> bootstrap.execute(id)).isInstanceOf(RuntimeException.class);
            assertThat(countAdmins()).isZero();
            assertThat(jdbc.queryForObject("select security_version from users where id = ?", Long.class, id.value()))
                    .isZero();
        } finally {
            jdbc.execute("alter table user_roles drop constraint test_no_admin");
        }
    }

    @Test
    void competingTransactionsPromoteOnlyOne() throws Exception {
        var first = user("concurrentone");
        var second = user("concurrenttwo");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject("select id from administrator_guard where id = 1 for update", Integer.class);
                locked.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var futures = java.util.List.of(first, second).stream()
                    .map(id -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        try {
                            return bootstrap.execute(id).name();
                        } catch (IllegalStateException e) {
                            return "REJECTED";
                        }
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            try {
                org.awaitility.Awaitility.await()
                        .atMost(java.time.Duration.ofSeconds(10))
                        .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%administrator_guard%for update%'",
                                        Integer.class))
                                .isGreaterThanOrEqualTo(2));
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            assertThat(java.util.List.of(
                            futures.get(0).get(30, TimeUnit.SECONDS),
                            futures.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PROMOTED", "REJECTED");
        }
        assertThat(countAdmins()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(security_version) from users", Long.class))
                .isEqualTo(1);
    }

    @Test
    void commandValidatesArgumentsAndRunsInNonWebMode() {
        assertThat(SecureFlowApplication.bootstrap(new String[] {"bootstrap-admin"}))
                .isEqualTo(2);
        assertThat(SecureFlowApplication.bootstrap(new String[] {"bootstrap-admin", "invalid"}))
                .isEqualTo(2);
        var id = user("commanduser");
        var keys = java.util.List.of("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD");
        var old = keys.stream().map(System::getProperty).toList();
        try {
            System.setProperty("DATABASE_URL", postgres.getJdbcUrl());
            System.setProperty("DATABASE_USERNAME", postgres.getUsername());
            System.setProperty("DATABASE_PASSWORD", postgres.getPassword());
            assertThat(SecureFlowApplication.bootstrap(
                            new String[] {"bootstrap-admin", id.value().toString()}))
                    .isZero();
            assertThat(countAdmins()).isEqualTo(1);
        } finally {
            for (int i = 0; i < keys.size(); i++) {
                if (old.get(i) == null) System.clearProperty(keys.get(i));
                else System.setProperty(keys.get(i), old.get(i));
            }
        }
    }

    @Test
    void policyRejectsRemovingTheLastActiveAdministrator() {
        var first = user("policyfirst");
        bootstrap.execute(first);
        var policy = new com.secureflow.identity.application.ActiveAdministratorPolicy(guard);
        assertThatThrownBy(
                        () -> transactions.executeWithoutResult(status -> policy.requireAnotherActiveAdministrator()))
                .isInstanceOf(IllegalStateException.class);
        var second = user("policysecond");
        jdbc.update("insert into user_roles (user_id, role) values (?, 'ADMIN')", second.value());
        transactions.executeWithoutResult(status -> policy.requireAnotherActiveAdministrator());
    }

    @Test
    void unavailableDatabaseFailsClosed() {
        var keys = java.util.List.of(
                "DATABASE_URL",
                "DATABASE_USERNAME",
                "DATABASE_PASSWORD",
                "spring.datasource.hikari.connection-timeout");
        var old = keys.stream().map(System::getProperty).toList();
        try {
            System.setProperty("DATABASE_URL", "jdbc:postgresql://127.0.0.1:1/unavailable?connectTimeout=1");
            System.setProperty("DATABASE_USERNAME", "unavailable");
            System.setProperty("DATABASE_PASSWORD", "unavailable");
            System.setProperty("spring.datasource.hikari.connection-timeout", "1000");
            assertThat(SecureFlowApplication.bootstrap(
                            new String[] {"bootstrap-admin", UUID.randomUUID().toString()}))
                    .isEqualTo(1);
            assertThat(countAdmins()).isZero();
        } finally {
            for (int i = 0; i < keys.size(); i++) {
                if (old.get(i) == null) System.clearProperty(keys.get(i));
                else System.setProperty(keys.get(i), old.get(i));
            }
        }
    }

    @Test
    void migrationCreatesExactlyOneGuard() {
        assertThat(jdbc.queryForObject("select count(*) from administrator_guard", Integer.class))
                .isEqualTo(1);
    }
}
