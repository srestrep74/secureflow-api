# Project Status

2026-11-15: Phase 4.2B registration HTTP adapter implemented. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed; 34 tests ran, including three HTTP registration tests, three existing registration transaction tests, nine architecture rules and explicitly enabled OpenAPI/Swagger checks. PostgreSQL Testcontainers verified persistence, conflicts and rollback. An actual database outage and production deployment remain unverified. Duplicate responses disclose account existence and require abuse controls. Phase 4.3 credential authentication is pending.

2026-11-15: Phase 4.2A registration application work implemented. Spotless and `clean check --no-daemon` passed: 30 tests, including three PostgreSQL registration tests and eight architecture checks. A credential constraint failure rolled back user and role creation; registration then succeeded with the same identity. HTTP registration remains Phase 4.2B; login remains outside this scope.

**Status: Phase 4.2B registration HTTP adapter implemented and verified; Phase 4.3 pending.**

2026-11-15: Phase 3 and Spotless approved. Phase 4.1 adds Argon2id hashing and a credential table linked to existing users. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed; 27 tests ran, including three credential integration tests and eight architecture rules. PostgreSQL Testcontainers verified migration V3 and credential constraints. Registration, login and protected administrator bootstrap remain outstanding; JWT and refresh belong to Phase 5. Production hashing costs require deployment benchmarking.

2026-11-15: Established the Java formatting baseline with Spotless 8.11.0 and Palantir Java Format 2.103.0. `spotlessApply`, `spotlessCheck`, and `clean check --no-daemon` passed; all 24 tests passed. A second apply preserved Java file hashes. A deliberately malformed file caused `check` to fail at `spotlessJavaCheck`; after restoration, `spotlessCheck` passed. Phase 4 remains unstarted.

2026-11-15: Implemented User and Task repository ports, JPA adapters, validated reconstruction, Flyway schema and optimistic concurrency. `gradlew.bat clean check --no-daemon` passed: 24 tests, including 8 architecture checks and 6 PostgreSQL persistence integration tests. Two coordinated transactions demonstrated rejection of a stale Task write and preservation of the committed change. PostgreSQL production deployment and later authentication and authorization remain unverified.

2026-11-15: The user approved Phase 2 and authorized Phase 3 persistence work. The Phase 2 domain version counts effective in-memory task mutations; persistence must preserve this contract while detecting concurrent writes. Phase 3 implementation and verification remain outstanding.

2026-11-15: Implemented independent User and Task domain aggregates, value objects, local invariants and ArchUnit boundaries. `gradlew.bat clean check --no-daemon` executed successfully, followed by `gradlew.bat check --no-daemon` after validation changes: 11 tests passed, including three architecture rules and two PostgreSQL Testcontainers integration tests. The last check was up-to-date. Persistence reconstitution, uniqueness, transactional concurrency, authorization and credential management remained future work at that point.

2026-11-15: The user approved Phase 1 and authorized Phase 2. The Compose smoke test started PostgreSQL 17 and its health check reached `healthy`; the isolated test container, network and volume were removed afterward. This verifies Compose startup, not production configuration.

2026-11-15: The user approved Phase 0 and ADR-0001 through ADR-0005 and authorized Phase 1 bootstrap. The original requirements file remains unchanged. Phase 1 foundation created with Java 25, Gradle 9.9.1, Spring Boot 4.0.9, PostgreSQL, Flyway and OpenAPI. The Gradle Wrapper resolved dependencies and `compileJava compileTestJava` passed. `gradlew.bat check --no-daemon` passed after the final changes: two integration tests started the web application with PostgreSQL Testcontainers, checked database connectivity and Flyway migration history, and verified health and enabled OpenAPI over HTTP. Production configuration remains unverified. Authentication and authorization functionality has not yet been implemented.

Approved security-state design: PostgreSQL primary remains authoritative; use one indexed projection and a replaceable port, signed version-bound authority snapshots and atomic refresh rotation. Redis is a future option only. Documentation review does not establish query performance or transaction correctness; these require implementation and tests in the approved later phase.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
