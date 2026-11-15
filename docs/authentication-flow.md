# Authentication Flow

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 4.2A adds the registration application use case. It validates 12–128 Unicode code points and at most 512 UTF-8 bytes, rejects blank or malformed Unicode passwords, and never normalizes them. A password-hashing port uses the existing Argon2id encoder. The transaction port delegates to Spring's transaction template: JPA flushes the user and roles before JDBC inserts the credential, and both operations join the same transaction. Database uniqueness constraints resolve concurrent collisions. Login and the HTTP registration adapter remain future work.

`CredentialRepositoryPort` stores and retrieves a PHC-formatted hash by user ID. Migration V3 enforces one credential per existing user. `PasswordEncoder` uses Spring Security Argon2id with a random 16-byte salt, 32-byte hash, 19,456 KiB memory, two iterations and parallelism one; Bouncy Castle provides the implementation. These parameters require deployment benchmarking.

JSON controller invokes an application use case through a credential authentication port. Its adapter invokes AuthenticationManager, DaoAuthenticationProvider, UserDetailsService and PasswordEncoder. Recheck user before issuing tokens. Refresh and logout use the family transaction protocol in the consolidated design.

Issuance reads current roles and security/catalog versions consistently while holding the user lock; a signed snapshot cannot combine stale grants with a new version. Refresh rotation atomically consumes one digest and creates one successor under a family lock. The separate strict replay rule revokes the family on reuse, including legitimate simultaneous retries. Redis cannot distinguish theft from such a retry.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
