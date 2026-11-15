package com.secureflow.task.application;

import com.secureflow.task.domain.*;
import com.secureflow.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface TaskRepositoryPort {
    void create(Task task);
    void update(Task task, long expectedVersion);
    Optional<Task> findById(TaskId id);
    List<Task> findByOwner(UserId ownerId, int page, int size);
}
