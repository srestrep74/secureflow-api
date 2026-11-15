package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.UserRepositoryPort;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.application.ConcurrentUpdateException;
import com.secureflow.shared.application.IntegrityConflictException;
import com.secureflow.shared.domain.UserId;
import com.secureflow.task.application.TaskRepositoryPort;
import com.secureflow.task.domain.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class PersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserRepositoryPort users;

    @Autowired
    TaskRepositoryPort tasks;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void reconstructsAggregatesAndRejectsStaleUpdates() {
        var user = User.register(
                new UserId(UUID.randomUUID()), new Username("TestUser"), new EmailAddress("Test@Example.com"));
        users.create(user);
        var loaded = users.findById(user.id()).orElseThrow();
        assertThat(loaded.username().value()).isEqualTo("testuser");
        assertThat(loaded.email().value()).isEqualTo("test@example.com");
        assertThat(loaded.roles()).containsExactly(Role.USER);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var task = Task.create(new TaskId(UUID.randomUUID()), user.id(), new TaskTitle("Task"), null, now);
        tasks.create(task);
        var first = tasks.findById(task.id()).orElseThrow();
        var stale = tasks.findById(task.id()).orElseThrow();
        first.edit(new TaskTitle("First"), null, now.plusSeconds(1));
        tasks.update(first, 0);
        stale.edit(new TaskTitle("Stale"), null, now.plusSeconds(1));
        assertThatThrownBy(() -> tasks.update(stale, 0)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(tasks.findById(task.id()).orElseThrow().title().value()).isEqualTo("First");
    }

    @Test
    void enforcesUniquenessAndOwnerForeignKey() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = User.register(
                new UserId(UUID.randomUUID()),
                new Username("user" + suffix),
                new EmailAddress(suffix + "@example.com"));
        users.create(user);
        assertThatThrownBy(() -> users.create(User.register(
                        new UserId(UUID.randomUUID()),
                        new Username("USER" + suffix),
                        new EmailAddress("other" + suffix + "@example.com"))))
                .isInstanceOf(IntegrityConflictException.class);
        assertThatThrownBy(() -> users.create(User.register(
                        new UserId(UUID.randomUUID()),
                        new Username("other" + suffix),
                        new EmailAddress(suffix.toUpperCase() + "@EXAMPLE.COM"))))
                .isInstanceOf(IntegrityConflictException.class);
        var task = Task.create(
                new TaskId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                new TaskTitle("Orphan"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"));
        assertThatThrownBy(() -> tasks.create(task)).isInstanceOf(IntegrityConflictException.class);
    }

    @Test
    void databaseRejectsNoncanonicalIdentityAndInvalidStatus() {
        var sql =
                "insert into users (id, username, email, status, security_version, lock_version) values (?, ?, ?, ?, 0, 0)";
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "UpperCase", "valid@example.com", "ACTIVE"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "validuser", "UPPER@example.com", "ACTIVE"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "validuser", "valid@example.com", "UNKNOWN"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void preservesRolesStatusAndVersions() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = User.register(
                new UserId(UUID.randomUUID()),
                new Username("user" + suffix),
                new EmailAddress(suffix + "@example.com"));
        users.create(user);
        user.assignRole(Role.ADMIN);
        user.changeStatus(AccountStatus.INACTIVE);
        users.update(user, 0);
        var restored = users.findById(user.id()).orElseThrow();
        assertThat(restored.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(restored.status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(restored.securityVersion()).isEqualTo(2);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var task = Task.create(new TaskId(UUID.randomUUID()), user.id(), new TaskTitle("Before"), "description", now);
        tasks.create(task);
        task.edit(new TaskTitle("After"), "description", now.plusSeconds(1));
        task.transitionTo(TaskStatus.IN_PROGRESS, now.plusSeconds(2));
        tasks.update(task, 0);
        var restoredTask = tasks.findById(task.id()).orElseThrow();
        assertThat(restoredTask.version()).isEqualTo(2);
        assertThat(restoredTask.ownerId()).isEqualTo(user.id());
        assertThat(restoredTask.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(restoredTask.createdAt()).isEqualTo(now);
        assertThat(restoredTask.modifiedAt()).isEqualTo(now.plusSeconds(2));
        Long lock = jdbc.queryForObject(
                "select lock_version from tasks where id = ?",
                Long.class,
                task.id().value());
        restoredTask.transitionTo(TaskStatus.IN_PROGRESS, now.plusSeconds(3));
        tasks.update(restoredTask, 2);
        assertThat(jdbc.queryForObject(
                        "select lock_version from tasks where id = ?",
                        Long.class,
                        task.id().value()))
                .isEqualTo(lock);
    }

    @Test
    void concurrentTransactionsRejectLostUpdate() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = User.register(
                new UserId(UUID.randomUUID()),
                new Username("user" + suffix),
                new EmailAddress(suffix + "@example.com"));
        users.create(user);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var task = Task.create(new TaskId(UUID.randomUUID()), user.id(), new TaskTitle("Initial"), null, now);
        tasks.create(task);
        var bothRead = new CountDownLatch(2);
        var firstCommitted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                transactions.executeWithoutResult(s -> {
                    var current = tasks.findById(task.id()).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    current.edit(new TaskTitle("Winner"), null, now.plusSeconds(1));
                    tasks.update(current, 0);
                });
                firstCommitted.countDown();
            });
            var second = executor.submit(() -> transactions.executeWithoutResult(s -> {
                var current = tasks.findById(task.id()).orElseThrow();
                bothRead.countDown();
                await(bothRead);
                await(firstCommitted);
                current.edit(new TaskTitle("Loser"), null, now.plusSeconds(1));
                tasks.update(current, 0);
            }));
            first.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ConcurrentUpdateException.class);
        }
        assertThat(tasks.findById(task.id()).orElseThrow().title().value()).isEqualTo("Winner");
    }

    @Test
    void preservesNanosecondTimestamps() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = User.register(
                new UserId(UUID.randomUUID()),
                new Username("user" + suffix),
                new EmailAddress(suffix + "@example.com"));
        users.create(user);
        var now = Instant.parse("2026-01-01T00:00:00.123456789Z");
        var task = Task.create(new TaskId(UUID.randomUUID()), user.id(), new TaskTitle("Time"), null, now);
        tasks.create(task);
        assertThat(tasks.findById(task.id()).orElseThrow().createdAt()).isEqualTo(now);
        assertThat(tasks.findByOwner(user.id(), 0, 10)).extracting(Task::id).contains(task.id());
        assertThat(tasks.findByOwner(new UserId(UUID.randomUUID()), 0, 10)).isEmpty();
        assertThatThrownBy(() -> tasks.findByOwner(user.id(), 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
