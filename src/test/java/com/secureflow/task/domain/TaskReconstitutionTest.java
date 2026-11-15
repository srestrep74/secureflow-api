package com.secureflow.task.domain;

import com.secureflow.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskReconstitutionTest {
    @Test
    void reconstructsStateAndRejectsInvalidHistory() {
        var id = new TaskId(UUID.randomUUID());
        var owner = new UserId(UUID.randomUUID());
        var time = Instant.parse("2026-01-01T00:00:00Z");
        var task = Task.reconstitute(id, owner, new TaskTitle("title"), "description", TaskStatus.DONE, time, time.plusSeconds(1), 2);
        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
        assertThat(task.version()).isEqualTo(2);
        assertThatThrownBy(() -> Task.reconstitute(id, owner, new TaskTitle("title"), null, TaskStatus.TODO, time, time.minusSeconds(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
