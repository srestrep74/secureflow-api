# Aggregate Design

`User` owns account status and role assignments and maintains its security version on effective changes. It cannot guarantee uniqueness, prevent changes to the last active administrator, serialize concurrent changes, authorize the caller, or revoke sessions by itself. Those rules require application orchestration and transaction-safe database constraints and locking. User creation initially grants USER; subsequent authorized role changes may remove any role. Account activation is the pragmatic registration default.

`Task` owns title, optional description, lifecycle and immutable owner identity. TODO and DONE may transition only to IN_PROGRESS; IN_PROGRESS may transition to either; same-state requests are no-ops. Completed tasks are editable. The version counts effective in-memory mutations and must be compared and stored atomically by persistence; it is not itself a concurrency lock. Creation and modification times are supplied explicitly for deterministic behavior. Cross-aggregate ownership existence and authorization require persistence and application coordination.

Reconstitution of persisted aggregates and coordination with JPA optimistic locking are Phase 3 responsibilities. No aggregate is a JPA entity and no User object is held by Task.
