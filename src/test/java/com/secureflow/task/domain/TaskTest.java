package com.secureflow.task.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.secureflow.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private Task task() {
        return Task.create(
                new TaskId(UUID.randomUUID()), new UserId(UUID.randomUUID()), new TaskTitle(" title "), null, NOW);
    }

    @Test
    void lifecycleAllowsOnlyAdjacentTransitionsAndNoOps() {
        var task = task();
        assertEquals(new TaskTitle("title"), task.title());
        assertThrows(IllegalStateException.class, () -> task.transitionTo(TaskStatus.DONE, NOW));
        assertEquals(TaskStatus.TODO, task.status());
        assertEquals(0, task.version());
        task.transitionTo(TaskStatus.IN_PROGRESS, NOW);
        task.transitionTo(TaskStatus.TODO, NOW);
        task.transitionTo(TaskStatus.IN_PROGRESS, NOW);
        task.transitionTo(TaskStatus.DONE, NOW);
        task.transitionTo(TaskStatus.DONE, NOW);
        assertThrows(IllegalStateException.class, () -> task.transitionTo(TaskStatus.TODO, NOW));
        task.transitionTo(TaskStatus.IN_PROGRESS, NOW);
        assertEquals(5, task.version());
    }

    @Test
    void editIsAtomicAndUpdatesVersionOnlyOnChange() {
        var task = task();
        var owner = task.ownerId();
        assertThrows(IllegalArgumentException.class, () -> task.edit(new TaskTitle("new"), "x".repeat(4001), NOW));
        assertEquals(new TaskTitle("title"), task.title());
        assertEquals(0, task.version());
        task.edit(new TaskTitle("new"), "x".repeat(4000), NOW.plusSeconds(1));
        task.edit(new TaskTitle("new"), "x".repeat(4000), NOW.plusSeconds(2));
        assertEquals(1, task.version());
        assertEquals(NOW.plusSeconds(1), task.modifiedAt());
        assertEquals(owner, task.ownerId());
        assertThrows(IllegalArgumentException.class, () -> task.transitionTo(TaskStatus.IN_PROGRESS, NOW));
    }

    @Test
    void valuesEnforceBoundariesAndNulls() {
        assertEquals(new TaskTitle("x"), new TaskTitle(" x "));
        assertEquals(200, new TaskTitle("x".repeat(200)).value().length());
        assertThrows(IllegalArgumentException.class, () -> new TaskTitle(" "));
        assertThrows(IllegalArgumentException.class, () -> new TaskTitle("x".repeat(201)));
        assertThrows(NullPointerException.class, () -> new TaskId(null));
        assertThrows(
                NullPointerException.class,
                () -> Task.create(new TaskId(UUID.randomUUID()), null, new TaskTitle("x"), null, NOW));
    }
}
