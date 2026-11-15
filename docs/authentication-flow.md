# Authentication Flow

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 4.2B exposes anonymous registration using the existing transactional use case. The servlet security chain permits POST registration and GET health, and explicitly enabled OpenAPI JSON; other requests are denied. The HTTP adapter validates the request and maps it to the application command. The application validates independently, hashes with Argon2id and atomically persists the user, USER role and credential. A login HTTP endpoint remains future work.

`CredentialRepositoryPort` stores and retrieves a PHC-formatted hash by user ID. Migration V3 enforces one credential per existing user. `PasswordEncoder` uses Spring Security Argon2id with a random 16-byte salt, 32-byte hash, 19,456 KiB memory, two iterations and parallelism one; Bouncy Castle provides the implementation. These parameters require deployment benchmarking.

JSON controller invokes an application use case through a credential authentication port. Its adapter invokes AuthenticationManager, DaoAuthenticationProvider, UserDetailsService and PasswordEncoder. Recheck user before issuing tokens. Refresh and logout use the family transaction protocol in the consolidated design.

Issuance reads current roles and security/catalog versions consistently while holding the user lock; a signed snapshot cannot combine stale grants with a new version. Refresh rotation atomically consumes one digest and creates one successor under a family lock. The separate strict replay rule revokes the family on reuse, including legitimate simultaneous retries. Redis cannot distinguish theft from such a retry.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.3 authenticates username or email programmatically. The identifier is trimmed and canonicalized by the existing domain values; `@` distinguishes email from username, whose grammar excludes it. `AuthenticateCredentialsUseCase` passes bounded, unmodified passwords through `CredentialAuthenticationPort`. The infrastructure adapter delegates to the explicitly configured `ProviderManager`, containing only `DaoAuthenticationProvider`. The provider loads the current user, roles and password hash through `UserDetailsService`, checks account status and calls the existing Argon2id `PasswordEncoder.matches`. Its successful principal carries the user ID and `ROLE_` authorities; the adapter returns only ID and username. ProviderManager erases credentials from the successful authentication. No security context or HTTP session is established.

Unknown users, malformed identifiers, wrong passwords and inactive accounts produce the same application error. A missing credential record or infrastructure failure produces a separate unavailable error; future HTTP handling must map these categories to 401 and 503 respectively. No login HTTP endpoint or login-issued tokens exist yet. Authentication alone is not an issuance authorization: Phase 5 must lock and recheck current status, roles and versions before issuing tokens, as approved in ADR-0004.

Phase 5.1B supplies a local RS256 issuer and claims validator, without connecting either to login or HTTP authentication. The caller must provide the real session-family ID and a consistent, current role/version snapshot; the component does not establish those facts. See [JWT validation](jwt-validation-flow.md). Credential authentication alone cannot safely issue a token until the approved transactional user and session checks exist.

Phase 5.2 foundation: `session_families` stores the UUID family ID, immutable user association, creation, absolute 30-day expiry and optional revocation time. Initial family and refresh digest inserts share one PostgreSQL transaction. Refresh secrets use 32 `SecureRandom` bytes encoded as unpadded Base64url; only lowercase SHA-256 hex digests are stored. Consumed records must be retained until family expiry plus seven days; cleanup and rotation are future work. A token is usable only when unconsumed, issued and unexpired, and its family is active and unexpired. Revocation records the first timestamp; repeated calls are no-ops.

Rotation is not implemented. Its future READ COMMITTED transaction must locate the digest, lock the user and then the family, reread all state, consume the old record and insert one successor atomically. Strict replay handling must commit family revocation before denial.
