# Phase 5 Security and Integration Closure Audit

Date: 2026-11-15. The user subsequently approved Phase 5 functional closure. Scope: committed Phase 5.6B and the focused corrections and regression tests in this audit. Phase 6 was not started.

## Executive summary

The implemented authentication lifecycle is functionally complete for Phase 5, subject to the verification and limitations below. Recommend proceeding to Phase 6 after the project phase approval gate. This is not a production-readiness assessment: essential operational controls remain outstanding. Integration tests provide evidence for specific scenarios, not proof against all failures or deployment conditions.

## Scope and methodology

Reviewed the accepted JWT, refresh, authority-freshness and bootstrap ADRs; application services, web and security adapters, PostgreSQL migrations, configuration and integration tests. Traced trust boundaries, SQL locks, transaction completion and failure translation. The existing application is a modular monolith; no external security service or new product capability was added.

## Verified authentication lifecycle

* Registration: `RegistrationController` and mapper validate input; `RegisterUserUseCase` creates a USER and Argon2id hash; `RegistrationTransactionPort` commits user, role and credential together. Bootstrap uses `AdministratorBootstrapService`, the database guard and a transaction; public input cannot select ADMIN.
* Login: `LoginController` -> `LoginUseCase` -> `AuthenticateCredentialsUseCase` -> `SpringCredentialAuthenticationAdapter` / `DaoAuthenticationProvider` -> user and credential persistence. `LoginTransactionPort` locks and reloads the user, verifies the catalog, then `SessionPersistenceAdapter` inserts the family and SHA-256 digest; `AccessTokenIssuer` signs. The transaction template returns after commit.
* Bearer: Spring Resource Server extracts the credential; `JwtCryptographyConfiguration` verifies RS256, trusted key and claims; `StatefulJwtAuthenticationConverter` invokes `SecurityStateValidator` and `SecurityStatePersistenceAdapter` against PostgreSQL before constructing authorities. The adapter requires a compatible persisted catalog. `ApiSecurityConfiguration` applies route authorization.
* Refresh: `RefreshController` -> `RefreshSessionUseCase` -> digest lookup and `RefreshTransactionPort`; user and family locks precede reread, consumption, successor insertion and signing. A successful result leaves the transaction only after commit. A consumed token returns a replay outcome so revocation commits before the controller returns 401.
* Logout: Resource Server and `SpringCurrentLogoutContext` supply trusted identity; `LogoutCurrentSessionUseCase` acquires the same user then family locks, checks ownership, revokes and returns only after commit; the controller sends 204.

## Security invariants and evidence

1–4: Registration assigns USER and uses Argon2id (16-byte salt, 32-byte hash, parallelism 1, 19,456 KiB, two iterations); invalid credentials do not create sessions; session family and initial digest share a transaction. Registration, credential and login integration tests cover these paths and rollback.

5–8: Signing is reached through credential-authenticated login or a valid refresh operation; no public standalone signing endpoint exists. Decoder and contract tests exercise the RS256 allowlist, key ID, issuer, audience, purpose, lifetime, 30-second skew, required claims and authority consistency; subject and session ownership are checked against persistent state. Five-minute lifetime is enforced.

9–13: Security state checks reject inactive users, revoked or expired families, mismatched security versions and foreign families. Missing/incompatible durable catalog fails closed. The decoder checks roles and the exact permission set before authorities are constructed. Role/status changes must follow the user-version and locking protocol; direct SQL bypasses the contract.

14–20: Refresh plaintext is not persisted; rotation consumes the prior digest. Replay revocation is committed before rejection. PostgreSQL unique constraints and user/family locks serialize rotations. Logout checks ownership and uses the same lock order. Existing tests inject immediate and deferred database failures for rotation, replay and logout; login signing and insert failures roll back. A deferred PostgreSQL trigger verifies that a login commit failure returns HTTP 500 without credentials or a persisted family.

21–22: The actual outage scenario below demonstrates fail-closed behavior at several boundaries; the chain uses `anyRequest().denyAll()`. This is not proof for every possible infrastructure exception or all routes.

## Integration, outage and recovery

`LoginHttpIntegrationTest` exercises registration, two independent sessions, A1/R1 -> A2/R2, both access tokens before and after current-family logout, rejection of R2, independence of the other family and a subsequent login. Separate tests exercise replay without conflating it with normal logout.

A dedicated PostgreSQL 17 Testcontainers instance is started with the application. A registered user logs in and accesses `/api/v1/me`; the test pauses that container, not a shared database. With test-only JDBC socket/connect timeouts and a bounded pool timeout, a valid JWT to `/api/v1/me` returns sanitized 503 and the MVC dispatch probe records no controller dispatch. Login credential lookup, refresh and authenticated logout also return 503. The container is unpaused in `finally`, readiness is polled using SQL, the existing JWT again succeeds, and the original refresh credential can rotate. The logout outage occurs during bearer security-state validation, before revocation; it does not independently interrupt a transaction already revoking a session. Session creation after successful credential lookup was not independently interrupted by a real outage; a separate injected transaction-creation exception at the persistence boundary verifies HTTP classification and no new family. Deferred failure tests cover refresh and logout commit separately. Test timeout configuration is not a production deployment configuration.

## Transactions and concurrency

Registration uses one transaction for JPA user/role and JDBC credential writes; bootstrap uses a Spring transaction and database guard. Login uses a new transaction and locks the user; refresh and logout use new transactions and acquire user then family locks. Login also takes a shared catalog row lock before issuance. Existing tests coordinate two threads and independent PostgreSQL transactions, observe database lock waits, and exercise refresh/refresh and both refresh/logout orders. Strict replay can revoke the family after a concurrent legitimate rotation, by accepted policy. No process-local synchronization provides the database invariant. Full concurrent role mutation during login, actual connection loss at login commit and a representative deadlock/load study were not directly exercised. No already-authorized request is retroactively cancelled.

## Spring Security and HTTP

Registration and health are explicitly public; login and refresh are public only when JWT is enabled; `/api/v1/me` and logout require authentication; remaining routes are denied. OpenAPI and Swagger are opt-in; only health is exposed by Actuator configuration. JWT Resource Server is opt-in; no custom JWT filter is used. Sessions are stateless; request cache, form login, HTTP Basic and default logout are disabled. CSRF is disabled for explicit header/body credentials without cookie authentication and must be revisited if transport changes. No cross-origin policy is enabled. An invalid Bearer header is processed even on a public route: existing refresh coverage rejects an expired header before MVC; clients should omit it. Filter-chain errors use a Bearer challenge for 401/403 and sanitized Problem Details; MVC errors are separately mapped. No successful logout or token response is returned after the tested transaction failures.

## JWT and authorization consistency

V6 initializes catalog version 1, matching `JwtAuthorityCatalog.VERSION`. Tokens contain a server-derived role and permission snapshot, user version and family ID. A signature alone does not grant access: each protected request reads current PostgreSQL state. A token with an incompatible catalog claim is rejected; an incompatible database catalog returns 503. Login now checks and locks the durable catalog before issuing. Rollouts changing catalog definitions must coordinate binaries and migration: mixed versions intentionally become unavailable, not silently trusted. This does not provide zero-downtime catalog upgrades.

## Sensitive data

Reviewed credential-bearing request, command, token result and refresh record string representations, exceptions and application-controlled logging. They redact secrets and digest values; the security-state adapter logs generic messages. Configuration references environment credentials and external RSA key paths. Git-tracked file names were checked for common private-key and environment-file extensions; no matching tracked private-key files were found. Tests use generated ephemeral key material and test credentials. This scoped inspection cannot prove the absence of every secret in all historical commits or external logs; deployment logging and secret storage remain operator responsibilities.

## Findings and remediation

| ID | Severity | Component and evidence | Impact and verification | Resolution |
| --- | --- | --- | --- | --- |
| P5-001 | Medium | Login used the compiled catalog without reading the persisted catalog. A regression test changed the database catalog to 2 and observed HTTP 200 with credentials. | Mixed deployments could create a session and deliver credentials immediately rejected by authoritative validation; no stale authorization was demonstrated. | Resolved: lock and validate the singleton catalog inside the login transaction before creation; incompatible or missing state returns 503. Regression checks no session is created. |
| P5-002 | Medium | Credential adapter treated arbitrary runtime exceptions as `AuthenticationUnavailableException`. | Unexpected internal failures could be reported as 503 instead of 500, obscuring diagnosis. Unit regression distinguishes classified storage unavailability from unexpected exceptions. Cause-less internal provider exceptions also map to 503; the current provider uses this for a missing credential record. | Resolved by limiting translation to recognized storage failures and missing authoritative credential state; unexpected failures propagate to sanitized 500 handling. |
| P5-003 | Medium | Login transaction startup: `CannotCreateTransactionException` was not classified by the MVC error handler. | A database connection failure after credential verification could return 500 instead of 503. | Resolved by classifying transaction-start unavailability and a focused HTTP regression injecting the exception at the session persistence boundary; this does not reproduce an actual transaction-start outage. |

No confirmed Critical or High finding was identified within this audit. This is a scoped conclusion, not a penetration test.

## Production-hardening backlog and residual risk

| Control | Status and next action |
| --- | --- |
| HTTPS and trusted proxy | Not implemented or deployment-verified; enforce TLS and explicitly configure trusted proxy handling. |
| Login/refresh rate limiting, brute-force and abuse detection | Not implemented; deploy appropriate controls and test them. |
| RSA rotation and overlap | Not implemented; one key ID is trusted, so replacement invalidates existing tokens. |
| Secret management | External paths/environment supported; production provisioning, access policy and rotation not verified. |
| PostgreSQL high availability and pool exhaustion | Not implemented or deployment-verified; test failover, latency, pool sizing and timeouts under load. |
| Refresh history retention and cleanup | Not implemented; establish bounded retention and cleanup without breaking replay policy. |
| Security audit logging, monitoring and replay alerts | Not implemented as an operational system; define safe events, monitoring and incident response. |
| Deployment environment and clock synchronization | Configuration exists; production deployment and clock tolerance have not been verified. |
| Key compromise and emergency revocation procedures | Not implemented as an operational runbook; define and rehearse procedures. |

Current deployment also needs restricted database writes, credential/body log redaction, Argon2 performance benchmarking and representative security-state query plans. Previously authorized requests may complete after revocation.

## Closure recommendation

`spotlessApply`, `spotlessCheck` and `clean check --no-daemon` succeeded; 150 tests ran with zero failures, errors or skips. Phase 5 can be treated as functionally complete and formally approved by the user; Phase 6 requires separate authorization. Correctness is supported by code review and targeted integration evidence, bounded by the untested scenarios above. Production readiness requires the operational work listed here and deployment-specific verification.
