package com.secureflow.task.domain;

import com.secureflow.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;

public final class Task {
    private final TaskId id;
    private final UserId ownerId;
    private final Instant createdAt;
    private TaskTitle title;
    private String description;
    private TaskStatus status;
    private Instant modifiedAt;
    private long version;

    private Task(TaskId id, UserId ownerId, TaskTitle title, String description, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "owner id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = validDescription(description);
        this.createdAt = Objects.requireNonNull(now, "time");
        this.modifiedAt = now;
        this.status = TaskStatus.TODO;
    }

    public static Task create(TaskId id, UserId ownerId, TaskTitle title, String description, Instant now) {
        return new Task(id, ownerId, title, description, now);
    }

    public TaskId id() { return id; }
    public UserId ownerId() { return ownerId; }
    public TaskTitle title() { return title; }
    public String description() { return description; }
    public TaskStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant modifiedAt() { return modifiedAt; }
    public long version() { return version; }

    public void edit(TaskTitle title, String description, Instant now) {
        Objects.requireNonNull(title, "title");
        String checked = validDescription(description);
        validateTime(now);
        if (!this.title.equals(title) || !Objects.equals(this.description, checked)) {
            long next = Math.incrementExact(version);
            this.title = title;
            this.description = checked;
            this.modifiedAt = now;
            this.version = next;
        }
    }

    public void transitionTo(TaskStatus next, Instant now) {
        Objects.requireNonNull(next, "status");
        validateTime(now);
        if (status == next) return;
        if ((status == TaskStatus.TODO && next != TaskStatus.IN_PROGRESS)
                || (status == TaskStatus.DONE && next != TaskStatus.IN_PROGRESS)) {
            throw new IllegalStateException("Invalid task status transition");
        }
        long version = Math.incrementExact(this.version);
        status = next;
        modifiedAt = now;
        this.version = version;
    }

    private void validateTime(Instant now) {
        Objects.requireNonNull(now, "time");
        if (now.isBefore(modifiedAt)) throw new IllegalArgumentException("Time cannot move backwards");
    }

    private static String validDescription(String description) {
        if (description != null && description.length() > 4000) throw new IllegalArgumentException("Description is too long");
        return description;
    }
}
