# Authentication Flow

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Phase 4.2B exposes anonymous registration using the existing transactional use case. The servlet security chain permits POST registration and GET health, and explicitly enabled OpenAPI JSON; other requests are denied. The HTTP adapter validates the request and maps it to the application command. The application validates independently, hashes with Argon2id and atomically persists the user, USER role and credential. Login remains future work.

`CredentialRepositoryPort` stores and retrieves a PHC-formatted hash by user ID. Migration V3 enforces one credential per existing user. `PasswordEncoder` uses Spring Security Argon2id with a random 16-byte salt, 32-byte hash, 19,456 KiB memory, two iterations and parallelism one; Bouncy Castle provides the implementation. These parameters require deployment benchmarking.

JSON controller invokes an application use case through a credential authentication port. Its adapter invokes AuthenticationManager, DaoAuthenticationProvider, UserDetailsService and PasswordEncoder. Recheck user before issuing tokens. Refresh and logout use the family transaction protocol in the consolidated design.

Issuance reads current roles and security/catalog versions consistently while holding the user lock; a signed snapshot cannot combine stale grants with a new version. Refresh rotation atomically consumes one digest and creates one successor under a family lock. The separate strict replay rule revokes the family on reuse, including legitimate simultaneous retries. Redis cannot distinguish theft from such a retry.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
