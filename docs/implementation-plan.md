# Implementation Plan

2026-11-15: Phase 5.4B implements opt-in HTTP JWT Resource Server authentication with PostgreSQL current-state validation and explicit GET /api/v1/me authorization. spotlessApply, spotlessCheck and clean check --no-daemon passed: 124 tests, no failures or skips, including eight HTTP Bearer tests and the existing architecture and PostgreSQL tests. The HTTP availability failure was injected; an outage of a running PostgreSQL server was not tested. READ COMMITTED does not retroactively revoke requests already in progress. Refresh rotation, replay handling, logout and task authorization remain deferred. Phase 5.5 has not started.

2026-11-15: Phase 5.4A implements a PostgreSQL authoritative security-state projection, catalog migration and framework-independent validator. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed: 115 tests, no failures or skips, including five PostgreSQL security-state integration tests and 11 architecture rules. A coordinated transaction tested committed account and session visibility; an unavailable local connection tested sanitized failure classification, not an outage of a running server. Query plan and latency were not measured. HTTP Bearer authentication remains Phase 5.4B and is not implemented.

2026-11-15: Phase 5.3B exposes opt-in stateless HTTP login with session-bound JWT and opaque refresh delivery, redacted DTOs and no-store headers. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed: 110 tests, no failures or skips, including PostgreSQL HTTP login and architecture checks. A classified persistence outage was simulated with a test double; a real PostgreSQL outage was not tested. TLS and login-abuse protections remain deployment requirements. Bearer authentication and online security-state checks remain Phase 5.4 work.

2026-11-15: Phase 5.3A implements application-level transactional login and session-bound access and refresh issuance. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed: 104 tests, including three PostgreSQL login integration tests, with no failures or skips. Login locks and reloads the user, persists the family and digest, signs before commit, and returns only after commit. Catalog version 1 is transitional; per-request freshness enforcement, HTTP login (Phase 5.3B), refresh rotation and cleanup remain unimplemented. Concurrent security mutation and commit-failure injection have not been exercised by the new tests.

2026-11-15: Phase 5.2 implements PostgreSQL session families and opaque refresh-token digest storage. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed: 101 tests, including three session persistence integration tests and 11 architecture rules, with no failures or skips. Creation is transactional; revocation is idempotent. Rotation, replay handling, cleanup, login, HTTP bearer authentication and PostgreSQL security-state validation remain unimplemented. Catalog version 1 requires durable initialization in Phase 5.4. Phase 5.3 has not started.

2026-11-15: Phase 5.1B implements RS256 access-token construction and structural claims validation. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed: 98 tests, including 43 claims contract cases, six cryptographic configuration tests and 11 architecture rules, with no failures or skips. HTTP bearer authentication, PostgreSQL security-state checks, persistent sessions and refresh remain unimplemented. Catalog version 1 must be coordinated with its future durable migration. See [JWT validation](jwt-validation-flow.md).

2026-11-15: Phase 5.1A implements opt-in external RSA key loading and Spring Security JOSE RS256 encoding and decoding, with a single trusted key identifier. `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` passed; 55 tests ran with no failures or skips, including six cryptographic configuration tests and existing PostgreSQL integration tests. No HTTP JWT authentication, complete claims validation, session checks or authorization is implemented. Phase 5.1B remains unstarted. See [JWT validation](jwt-validation-flow.md) and [key operations](security-decisions.md).

2026-11-15: Phase 4.4 implements ADR-0005 operator bootstrap, the PostgreSQL guard and reusable last-administrator policy. `spotlessApply`, `spotlessCheck`, and `clean check bootJar --no-daemon` passed: 49 tests, including eight bootstrap integration tests and 11 architecture rules. Concurrent transactions were observed waiting on the guard before one promotion and one rejection. Actual database unavailability, rollback, command startup and idempotency were exercised. Future administrative mutations must adopt the guard and policy; direct database writes bypass them. At that point Phase 5 was not authorized or started.

2026-11-15: Phase 4.3 adds programmatic credential authentication through the application port, explicit ProviderManager and DaoAuthenticationProvider, current user/credential lookup and Argon2id verification. Spotless and `clean check --no-daemon` passed with 40 tests. HTTP login, token issuance and protected administrator bootstrap remain subsequent work.

2026-11-15: Phase 4.2B registration HTTP adapter, security chain, request validation, error responses, OpenAPI and PostgreSQL HTTP integration tests implemented and verified with `spotlessApply`, `spotlessCheck` and `clean check --no-daemon` (34 tests). Phase 4.3 credential authentication remains pending.

2026-11-15: Phase 4.2A implements the transactional registration use case; the HTTP adapter and its security configuration remain Phase 4.2B.

**Status: Phase 5.4B implemented and verified; Phase 5.5 has not started.**

2026-11-15: Phase 3 and formatting approved. Phase 4.1 adds an Argon2id encoder, credential persistence port and PostgreSQL migration. Registration, authentication and operator bootstrap remain outstanding. JWT and refresh remain Phase 5.

Formatting quality gate (2026-11-15): Spotless 8.11.0 and Palantir Java Format 2.103.0 configured and applied to Java sources. Formatting checks, the clean Gradle check (24 tests), idempotence, and rejection of an intentional formatting violation were verified. See [code style](code-style.md). This does not authorize Phase 4.

Phase 3 checklist (2026-11-15): repository ports and JPA adapters, aggregate reconstruction, Flyway business schema, normalized uniqueness, ownership foreign key, optimistic locking and PostgreSQL integration tests implemented. `gradlew.bat clean check --no-daemon` passed with 24 tests. Phase 4 has not started.

2026-11-15: Phase 2 approved and Phase 3 authorized. Persistence adapters, database constraints, aggregate reconstitution, concurrency verification and documentation remain outstanding.

Phase 2 checklist (2026-11-15): User and Task aggregates, value objects, local invariants, unit tests and architecture tests implemented; complete Gradle check and existing PostgreSQL integration tests passed. Persistence-level uniqueness, owner integrity and optimistic concurrency, aggregate reconstitution, credential handling and authorization are deferred to their respective later phases.

2026-11-15: Phase 1 was approved by the user. Compose started PostgreSQL 17 and reported a healthy service; its isolated test resources were removed. Phase 2 domain modeling is authorized.

Phase 0 decisions and Phase 1 were approved by the user on 2026-11-15. Phase 2: domain and invariant tests. Phase 3: persistence, constraints and concurrency tests. Phase 4: registration, credentials and administrator bootstrap; no token promise. Phase 5: JWT, online state, refresh and logout together with security tests. Phase 6: task/admin API, authorization and negative tests. Phase 7: OpenAPI and contract tests. Phase 8: operational hardening review. Phase 9: full assessment. Stop for review between phases; security controls accompany features.

Phase 1 evidence (2026-11-15): Wrapper and Java 25 configured; dependency resolution and compilation passed; `check` passed with PostgreSQL Testcontainers, Flyway, HTTP health and OpenAPI tests. Compose startup reached healthy status. Phase 1 human review is complete and Phase 2 is authorized.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
