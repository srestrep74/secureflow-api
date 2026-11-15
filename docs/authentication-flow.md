# Authentication Flow

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 4.2B exposes anonymous registration using the existing transactional use case. The servlet security chain permits POST registration and GET health, and explicitly enabled OpenAPI JSON; other requests are denied. The HTTP adapter validates the request and maps it to the application command. The application validates independently, hashes with Argon2id and atomically persists the user, USER role and credential. At that phase, a login HTTP endpoint remained future work.

`CredentialRepositoryPort` stores and retrieves a PHC-formatted hash by user ID. Migration V3 enforces one credential per existing user. `PasswordEncoder` uses Spring Security Argon2id with a random 16-byte salt, 32-byte hash, 19,456 KiB memory, two iterations and parallelism one; Bouncy Castle provides the implementation. These parameters require deployment benchmarking.

JSON controller invokes an application use case through a credential authentication port. Its adapter invokes AuthenticationManager, DaoAuthenticationProvider, UserDetailsService and PasswordEncoder. Recheck user before issuing tokens. Refresh and logout use the family transaction protocol in the consolidated design.

Issuance reads current roles and security/catalog versions consistently while holding the user lock; a signed snapshot cannot combine stale grants with a new version. Refresh rotation atomically consumes one digest and creates one successor under a family lock. The separate strict replay rule revokes the family on reuse, including legitimate simultaneous retries. Redis cannot distinguish theft from such a retry.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.3 authenticates username or email programmatically. The identifier is trimmed and canonicalized by the existing domain values; `@` distinguishes email from username, whose grammar excludes it. `AuthenticateCredentialsUseCase` passes bounded, unmodified passwords through `CredentialAuthenticationPort`. The infrastructure adapter delegates to the explicitly configured `ProviderManager`, containing only `DaoAuthenticationProvider`. The provider loads the current user, roles and password hash through `UserDetailsService`, checks account status and calls the existing Argon2id `PasswordEncoder.matches`. Its successful principal carries the user ID and `ROLE_` authorities; the adapter returns only ID and username. ProviderManager erases credentials from the successful authentication. No security context or HTTP session is established.

Unknown users, malformed identifiers, wrong passwords and inactive accounts produce the same application error. A missing credential record or infrastructure failure produces a separate unavailable error; HTTP handling maps these categories to 401 and 503 respectively. Authentication alone is not an issuance authorization: Phase 5 must lock and recheck current status, roles and versions before issuing tokens, as approved in ADR-0004.

Phase 5.1B supplies a local RS256 issuer and claims validator, without connecting either to login or HTTP authentication. The caller must provide the real session-family ID and a consistent, current role/version snapshot; the component does not establish those facts. See [JWT validation](jwt-validation-flow.md). Credential authentication alone cannot safely issue a token until the approved transactional user and session checks exist.

Phase 5.2 foundation: `session_families` stores the UUID family ID, immutable user association, creation, absolute 30-day expiry and optional revocation time. Initial family and refresh digest inserts share one PostgreSQL transaction. Refresh secrets use 32 `SecureRandom` bytes encoded as unpadded Base64url; only lowercase SHA-256 hex digests are stored. Consumed records must be retained until family expiry plus seven days; cleanup remains future work; rotation is implemented in Phase 5.5A. A token is usable only when unconsumed, issued and unexpired, and its family is active and unexpired. Revocation records the first timestamp; repeated calls are no-ops.

Phase 5.5A implemented rotation at the application boundary. A READ COMMITTED transaction locates the indexed digest, locks the user and then the family, rereads state, and consumes the old record before inserting one successor. A typed replay outcome commits family revocation before the application rejects the request.

## Phase 5.3A: application login

`LoginUseCase` delegates credentials to the existing AuthenticationManager-backed use case. After authentication, a new transaction locks the user row, reloads the account and roles, rejects missing or inactive users, creates a 720-hour family and one initial refresh digest, and signs the access token. The transaction commits before the application result is returned. JWT issuance is available only when JWT configuration is enabled and valid; HTTP login was not implemented in Phase 5.3A.

Security-state writers must update and lock the user row in the same transaction as changes to roles or status. A change committed before the lock is reflected in issuance; a concurrent change waits for the login transaction or precedes its snapshot. Later changes require the online enforcement planned for Phase 5.4. An HTTP response can fail after commit, leaving an abandoned session; expiry limits its lifetime and future cleanup must remove expired records. Cleanup is not implemented.

## Phase 5.3B: HTTP login

The stateless security chain permits POST login only when JWT is enabled. The controller maps the validated JSON to `LoginCommand` and delegates to `LoginUseCase`. AuthenticationManager runs through the existing credential adapter without establishing an HTTP session or authenticated security context. The transaction persists the session and refresh digest and signs the access JWT before commit; the controller returns tokens only after the use case completes. A lost HTTP response after commit can leave a valid abandoned session until expiry and future cleanup. A signed JWT cannot authenticate protected HTTP requests until Phase 5.4 implements Resource Server and online security-state validation.

## Phase 5.4B HTTP bearer integration

The opt-in Resource Server extracts Bearer tokens and authenticates each request through JwtAuthenticationProvider and the configured NimbusJwtDecoder. After claims validation, StatefulJwtAuthenticationConverter invokes SecurityStateValidator before constructing authentication. The SecurityContext is request-bound and stateless; no HTTP session is created. Database unavailability is 503, invalid token or current state is 401, and insufficient authority is 403. At that phase, refresh and logout remained deferred.

## Phase 5.5A refresh rotation

The command carries only the opaque secret. The application validates its representation, hashes it with SHA-256, and uses the unique digest index to resolve the family and user. The transaction locks the user row and then the family row and rereads user, family and token state. Active accounts and families with unexpired, unused tokens can rotate. Current persisted roles, security version and the compatible catalog determine the signed five-minute access JWT. The successor remains in the same family and expires at its original absolute 720-hour deadline. PostgreSQL executes the consumption update before the successor insert, preserving the partial unique index; both writes and signing succeed or roll back together. Results leave the application only after commit.

A known consumed token presented before expiry causes family revocation and a typed replay outcome. The transaction commits this outcome before the application throws the safe replay exception. Expired or unknown secrets do not trigger revocation. Consumed digests remain available for replay detection; cleanup after family expiry plus seven days remains future work. A failed database commit is an infrastructure failure, including a failed replay revocation. The Resource Server rejects subsequent access JWTs for the revoked family. Concurrent legitimate refreshes and retrying a response lost after commit can trigger the strict replay policy; clients must serialize refresh and reauthenticate after an uncertain result. No grace period is provided.

## Phase 5.5B HTTP refresh

The controller delegates without an encompassing transaction. Rotation consumes the old digest and inserts its successor in the same family without extending expiry. Replay commits family revocation before the generic HTTP 401. Clients must atomically replace the refresh credential and serialize refresh attempts; a lost response or concurrent reuse requires authentication again. Normal rotation leaves unexpired access JWTs valid; replay revocation invalidates the family through current-state checks.

## Phase 5.6A current-session logout

The application accepts a trusted user and family identity, never a public client-selected session. A new transaction locks the user row then the owned family row and reloads family state before revoking it. A missing user, foreign or missing family, invalid input or inactive unrevoked family is an invalid context; an already revoked owned family succeeds without a write. Database and commit failures are unavailable outcomes. The transaction commits before completion is returned. No credentials, roles, security version, expiration, or other families are changed.

Refresh follows the same lock order. If logout commits first, refresh rejects the revoked family; if refresh commits first, logout revokes the same family including its successor. Existing online checks reject access JWTs after the revocation commits; previously authenticated requests may finish. Phase 5.6B subsequently exposes this behavior over HTTP.

## Phase 5.6B: HTTP logout lifecycle

The Resource Server validates the access JWT and current PostgreSQL security state before the infrastructure context adapter extracts `sub` and `sid` from the authenticated JWT. The controller passes the resulting application command to the existing transactional use case. Its user-then-family locks serialize logout with refresh, and the response follows successful commit. A revoked family invalidates its access and refresh credentials on subsequent requests; other families, user status, roles and security version are unchanged. A request already authenticated before commit may finish. Spring Security clears its request-bound context through its normal stateless lifecycle; clearing that context alone would not revoke persistent credentials. Clients must discard both local tokens. Application idempotency does not permit a second HTTP call with a revoked JWT: authentication rejects it with 401.
