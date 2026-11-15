# Testing Strategy

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 3 persistence tests use PostgreSQL 17 Testcontainers and Flyway, real adapters, uniqueness and foreign-key violations, round trips, no-op versions and two coordinated independent transactions. Architecture tests verify dependency direction. HTTP bearer authentication, 401/403/404 and owner filtering, database outage, secret redaction, principal isolation, real PostgreSQL refresh and admin races and rollback-safe revocation remain future work.

Verify one indexed security-state statement per authenticated request, no aggregate or role fetch, parameterization and representative execution plans. Exercise role assignment/removal and no-ops, account and credential changes, catalog deployment mismatch, consistent login/refresh snapshots during concurrent role changes, post-commit old-token rejection, session-only revocation, DB 503 and already-authorized request semantics. Separately assert at-most-one rotation and the selected family-revocation replay policy, including simultaneous requests and lost responses.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.2B adds HTTP tests against the real server and PostgreSQL Testcontainers for public registration, safe output, Argon2id persistence, default role, malformed and invalid input, duplicate identities and denied routes. Architecture rules prohibit web-to-persistence and application-to-web dependencies. Existing registration transaction and rollback tests remain in place. Full verification and any limitations are recorded in project status.

Phase 4.3 tests exercise real DaoAuthenticationProvider and Argon2id with PostgreSQL registration, username and email normalization, generic failure responses, disabled accounts, missing credentials, principal and role mapping, and concurrent identity isolation. A provider test injects a repository failure and verifies that it remains distinguishable from invalid credentials. Architecture rules restrict Spring Security dependencies and verify the port adapter. A real PostgreSQL outage and HTTP 401/503 mapping are not exercised in this phase.

Phase 5.1B tests real RS256 signing and decoding using generated keys and a fixed clock. Parameterized negative cases cover missing, malformed and incorrect claims, timestamp ordering and lifetime, unsupported versions, role and permission mismatch, and key ID. Existing cryptographic tests cover wrong keys, payload tampering, unsupported algorithms and key configuration. These tests do not exercise HTTP bearer authentication or PostgreSQL security state.
