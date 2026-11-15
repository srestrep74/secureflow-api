package com.secureflow.task.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
class TaskJpaEntity {
    @Id UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) UUID ownerId;
    @Column(nullable = false, length = 200) String title;
    @Column(length = 4000) String description;
    @Column(nullable = false, length = 16) String status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_nanos", nullable = false, updatable = false) short createdNanos;
    @Column(name = "modified_at", nullable = false) Instant modifiedAt;
    @Column(name = "modified_nanos", nullable = false) short modifiedNanos;
    @Column(name = "domain_version", nullable = false) long domainVersion;
    @Version @Column(name = "lock_version", nullable = false) Long lockVersion;
    protected TaskJpaEntity() { }
}
