# Testing Strategy

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

No tests have been executed. Test domain invariants, actual JWT decoder negative cases, 401/403/404 and owner filtering, database outage, secret redaction, principal isolation, real PostgreSQL refresh and admin races, rollback-safe revocation, migrations, uniqueness and optimistic conflicts. Architecture tests verify dependency direction.

Verify one indexed security-state statement per authenticated request, no aggregate or role fetch, parameterization and representative execution plans. Exercise role assignment/removal and no-ops, account and credential changes, catalog deployment mismatch, consistent login/refresh snapshots during concurrent role changes, post-commit old-token rejection, session-only revocation, DB 503 and already-authorized request semantics. Separately assert at-most-one rotation and the selected family-revocation replay policy, including simultaneous requests and lost responses.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
