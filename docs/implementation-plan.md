# Implementation Plan

**Status: Phase 2 implemented and verified; Phase 3 awaits approval.**

Phase 2 checklist (2026-11-15): User and Task aggregates, value objects, local invariants, unit tests and architecture tests implemented; complete Gradle check and existing PostgreSQL integration tests passed. Persistence-level uniqueness, owner integrity and optimistic concurrency, aggregate reconstitution, credential handling and authorization are deferred to their respective later phases. Phase 3 is not authorized.

2026-11-15: Phase 1 was approved by the user. Compose started PostgreSQL 17 and reported a healthy service; its isolated test resources were removed. Phase 2 domain modeling is authorized.

Phase 0 decisions and Phase 1 were approved by the user on 2026-11-15. Phase 2: domain and invariant tests. Phase 3: persistence, constraints and concurrency tests. Phase 4: registration, credentials and administrator bootstrap; no token promise. Phase 5: JWT, online state, refresh and logout together with security tests. Phase 6: task/admin API, authorization and negative tests. Phase 7: OpenAPI and contract tests. Phase 8: operational hardening review. Phase 9: full assessment. Stop for review between phases; security controls accompany features.

Phase 1 evidence (2026-11-15): Wrapper and Java 25 configured; dependency resolution and compilation passed; `check` passed with PostgreSQL Testcontainers, Flyway, HTTP health and OpenAPI tests. Compose startup reached healthy status. Phase 1 human review is complete and Phase 2 is authorized.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
