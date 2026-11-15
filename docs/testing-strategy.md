# Testing Strategy

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 3 persistence tests use PostgreSQL 17 Testcontainers and Flyway, real adapters, uniqueness and foreign-key violations, round trips, no-op versions and two coordinated independent transactions. Architecture tests verify dependency direction. HTTP bearer authentication, 401/403/404 and owner filtering, database outage, secret redaction, principal isolation, real PostgreSQL refresh and admin races and rollback-safe revocation remain future work.

Verify one indexed security-state statement per authenticated request, no aggregate or role fetch, parameterization and representative execution plans. Exercise role assignment/removal and no-ops, account and credential changes, catalog deployment mismatch, consistent login/refresh snapshots during concurrent role changes, post-commit old-token rejection, session-only revocation, DB 503 and already-authorized request semantics. Separately assert at-most-one rotation and the selected family-revocation replay policy, including simultaneous requests and lost responses.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.2B adds HTTP tests against the real server and PostgreSQL Testcontainers for public registration, safe output, Argon2id persistence, default role, malformed and invalid input, duplicate identities and denied routes. Architecture rules prohibit web-to-persistence and application-to-web dependencies. Existing registration transaction and rollback tests remain in place. Full verification and any limitations are recorded in project status.

Phase 4.3 tests exercise real DaoAuthenticationProvider and Argon2id with PostgreSQL registration, username and email normalization, generic failure responses, disabled accounts, missing credentials, principal and role mapping, and concurrent identity isolation. A provider test injects a repository failure and verifies that it remains distinguishable from invalid credentials. Architecture rules restrict Spring Security dependencies and verify the port adapter. A real PostgreSQL outage and HTTP 401/503 mapping are not exercised in this phase.

Phase 5.1B tests real RS256 signing and decoding using generated keys and a fixed clock. Parameterized negative cases cover missing, malformed and incorrect claims, timestamp ordering and lifetime, unsupported versions, role and permission mismatch, and key ID. Existing cryptographic tests cover wrong keys, payload tampering, unsupported algorithms and key configuration. These tests do not exercise HTTP bearer authentication or PostgreSQL security state.

Phase 5.2 adds PostgreSQL Testcontainers coverage for session persistence, digest lookup, rollback on duplicate digest, foreign keys and idempotent revocation. Rotation, replay behavior, online security-state validation and retention cleanup require later integration tests.

## Phase 5.3A

Login integration tests exercise real Spring Security authentication, PostgreSQL persistence, and RS256 signing and decoding with test-only generated keys. They cover invalid credentials, disabled users, digest storage, server-derived claims, signing rollback, a database uniqueness failure and a stale authentication result followed by deactivation. Full concurrent security mutation, deferred commit failure and HTTP response delivery are not exercised by these tests.

## Phase 5.3B HTTP verification

HTTP integration coverage uses PostgreSQL Testcontainers, the real credential provider, ephemeral RSA keys and the JWT decoder. It checks token claims and persisted session and digest, generic credential failures, input rejection, cache headers, stateless behavior and denial of protected routes. A dependency double does not demonstrate a real database outage; distinguish simulated failures from actual unavailability in test reports.

Phase 5.4A adds PostgreSQL 17 Testcontainers coverage for state projections, missing or foreign accounts and sessions, role/status changes, expiration and idempotent revocation, catalog initialization and incompatibility, and schema indexes. A separate connection to an unavailable local PostgreSQL port tests sanitized failure classification; this is a broken-connection test, not a running server outage. Existing HTTP, JWT, persistence and architecture suites remain regression gates. No latency benchmark or representative query plan is claimed.

A coordinated two-transaction test checks the committed account and family projection before and after a combined change. The final Gradle check executed 115 tests with no failures or skips, including five security-state integration tests and 11 architecture rules.

## Phase 5.4B HTTP bearer integration

HTTP integration tests use real RS256 tokens, a running web server and PostgreSQL 17 Testcontainers. They cover current-state rejection, signed invalid claims, 401/403/503 distinctions, explicit authority mapping and concurrent principal isolation. The classified database failure test injects an exception; it does not simulate an outage of a running PostgreSQL server. The test-only protected controller and security chain exercise role and permission checks without publishing a production diagnostic route.

## Phase 5.5A refresh verification

PostgreSQL 17 integration tests exercise real signing and decoding, digest persistence, current role changes, expiration, replay revocation and bearer rejection. Coordinated transactions hold the first issuance while PostgreSQL reports the second waiting on the user lock; the second then detects replay. Database triggers inject consumption, insertion and deferred commit failures, including replay revocation commit; signing failure is injected through the application token port. These tests are not a production load measurement or a running database outage. Foreign keys prevent a family from referencing a deleted user in the normal schema; the defensive missing-user branch is not established by such a database state.
