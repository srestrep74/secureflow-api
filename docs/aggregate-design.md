# Aggregate Design

`User` owns account status and role assignments and maintains its security version on effective changes. It cannot guarantee uniqueness, prevent changes to the last active administrator, serialize concurrent changes, authorize the caller, or revoke sessions by itself. Those rules require application orchestration and transaction-safe database constraints and locking. User creation initially grants USER; subsequent authorized role changes may remove any role. Account activation is the pragmatic registration default.

`Task` owns title, optional description, lifecycle and immutable owner identity. TODO and DONE may transition only to IN_PROGRESS; IN_PROGRESS may transition to either; same-state requests are no-ops. Completed tasks are editable. The version counts effective in-memory mutations and must be compared and stored atomically by persistence; it is not itself a concurrency lock. Creation and modification times are supplied explicitly for deterministic behavior. Cross-aggregate ownership existence and authorization require persistence and application coordination.

No aggregate is a JPA entity and no User object is held by Task. Task domain version counts effective mutations; a separate Hibernate `@Version` counts writes. Updates supply the version observed before mutations, compare it against stored domain state, and flush a managed entity. Hibernate detects a competing write after that read. No-op updates do not dirty the entity. User security version similarly represents security changes, not the Hibernate lock counter. Conflicts become application exceptions; future HTTP handling should map optimistic conflicts to 409 after authorization.

Phase 4.4 reuses `User.assignRole`: an effective change advances `securityVersion`, while the sole administrator retry does not call the repository update. The system-wide count is coordinated outside the aggregate by a PostgreSQL guard. Future administrative mutations still need to invoke the policy under that guard.

A session family is an Identity technical consistency root with an immutable UUID, user ID, creation time and absolute expiry. Revocation is monotonic through a conditional database update. A refresh record retains its digest and consumption time independently of the User aggregate. The unconsumed-token partial unique index supports consume-then-insert rotation under a family lock.

## Phase 5.5A rotation

The family is the serialization boundary for refresh and replay, after the user lock. The old refresh record is consumed before a successor is inserted in the same transaction. Revocation is monotonic and does not erase consumed records. Family creation and absolute expiry are unchanged during rotation.
