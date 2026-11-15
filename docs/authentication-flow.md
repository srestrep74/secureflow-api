# Authentication Flow

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

JSON controller invokes an application use case through a credential authentication port. Its adapter invokes AuthenticationManager, DaoAuthenticationProvider, UserDetailsService and PasswordEncoder. Recheck user before issuing tokens. Refresh and logout use the family transaction protocol in the consolidated design.

Issuance reads current roles and security/catalog versions consistently while holding the user lock; a signed snapshot cannot combine stale grants with a new version. Refresh rotation atomically consumes one digest and creates one successor under a family lock. The separate strict replay rule revokes the family on reuse, including legitimate simultaneous retries. Redis cannot distinguish theft from such a retry.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
