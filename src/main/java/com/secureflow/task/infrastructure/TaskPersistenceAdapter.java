package com.secureflow.task.infrastructure;

import com.secureflow.shared.application.ConcurrentUpdateException;
import com.secureflow.shared.application.IntegrityConflictException;
import com.secureflow.shared.domain.UserId;
import com.secureflow.task.application.TaskRepositoryPort;
import com.secureflow.task.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TaskPersistenceAdapter implements TaskRepositoryPort {
    private final SpringTaskRepository repository;
    private final TaskPersistenceMapper mapper = new TaskPersistenceMapper();

    TaskPersistenceAdapter(SpringTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void create(Task task) {
        try {
            repository.saveAndFlush(mapper.toEntity(task));
        } catch (DataIntegrityViolationException e) {
            throw new IntegrityConflictException("Task integrity conflict", e);
        }
    }

    @Override
    @Transactional
    public void update(Task task, long expectedVersion) {
        var entity = repository.findById(task.id().value()).orElseThrow();
        if (entity.domainVersion != expectedVersion || task.version() < expectedVersion)
            throw new ConcurrentUpdateException("Stale task");
        if (!entity.ownerId.equals(task.ownerId().value())
                || !entity.createdAt.plusNanos(entity.createdNanos).equals(task.createdAt()))
            throw new IllegalArgumentException("Task identity cannot change");
        if (task.version() == expectedVersion
                && (!entity.title.equals(task.title().value())
                        || !java.util.Objects.equals(entity.description, task.description())
                        || !entity.status.equals(task.status().name())
                        || !entity.modifiedAt.plusNanos(entity.modifiedNanos).equals(task.modifiedAt())))
            throw new IllegalArgumentException("Task changed without advancing its version");
        mapper.copy(task, entity);
        try {
            repository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException("Stale task", e);
        } catch (DataIntegrityViolationException e) {
            throw new IntegrityConflictException("Task integrity conflict", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Task> findById(TaskId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findByOwner(UserId ownerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page");
        return repository.findByOwnerIdOrderById(ownerId.value(), PageRequest.of(page, size)).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
