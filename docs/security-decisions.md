# Security Decisions

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Accepted decisions specify Argon2id, RS256, five-minute access JWT, online security and session state, opaque refresh rotation, explicit HTTPS transport and deny-by-default authorization. PostgreSQL unavailability fails closed. Implementation remains scheduled for later phases.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.2B uses a stateless security filter chain with explicit registration and health allowlists and deny-by-default for all other routes. Form login, HTTP Basic, logout and request caching are disabled. CSRF is disabled because the API does not use browser cookies or session authentication and future credentials are explicitly supplied in Bearer authorization headers; reassess this decision if credential transport changes. No cross-origin access is enabled; any future CORS policy must explicitly identify trusted origins. OpenAPI remains disabled by default. A duplicate response reveals that an identifier is registered; this privacy trade-off follows the approved 409 contract and requires abuse controls before production.

Phase 4.3 uses the standard DAO provider and the existing Argon2id encoder, with one explicit ProviderManager and no HTTP session. Credential authentication errors do not disclose whether an account exists. Missing credential records and infrastructure failures are distinct from bad credentials. Passwords are not normalized and credential-bearing command string representations are redacted. Authentication results do not include credentials. Login rate limiting and production password-hashing benchmarks remain required before deployment.
