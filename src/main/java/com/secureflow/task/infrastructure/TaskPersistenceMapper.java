package com.secureflow.task.infrastructure;

import com.secureflow.shared.domain.UserId;
import com.secureflow.task.domain.*;
import java.time.temporal.ChronoUnit;

final class TaskPersistenceMapper {
    TaskJpaEntity toEntity(Task task) {
        var entity = new TaskJpaEntity();
        entity.id = task.id().value();
        entity.ownerId = task.ownerId().value();
        entity.createdAt = task.createdAt().truncatedTo(ChronoUnit.MICROS);
        entity.createdNanos = (short) (task.createdAt().getNano() % 1000);
        copy(task, entity);
        return entity;
    }

    void copy(Task task, TaskJpaEntity entity) {
        entity.title = task.title().value();
        entity.description = task.description();
        entity.status = task.status().name();
        entity.modifiedAt = task.modifiedAt().truncatedTo(ChronoUnit.MICROS);
        entity.modifiedNanos = (short) (task.modifiedAt().getNano() % 1000);
        entity.domainVersion = task.version();
    }

    Task toDomain(TaskJpaEntity entity) {
        return Task.reconstitute(
                new TaskId(entity.id),
                new UserId(entity.ownerId),
                new TaskTitle(entity.title),
                entity.description,
                TaskStatus.valueOf(entity.status),
                entity.createdAt.plusNanos(entity.createdNanos),
                entity.modifiedAt.plusNanos(entity.modifiedNanos),
                entity.domainVersion);
    }
}
