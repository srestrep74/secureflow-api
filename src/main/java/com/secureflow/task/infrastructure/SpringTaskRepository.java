package com.secureflow.task.infrastructure;

import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringTaskRepository extends JpaRepository<TaskJpaEntity, UUID> {
    List<TaskJpaEntity> findByOwnerIdOrderById(UUID ownerId, Pageable pageable);
}
