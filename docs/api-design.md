# API Design

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Use versioned JSON over HTTPS. Register, login, refresh and logout under /api/v1/auth, plus /me, task and administrative resources. Derive task owner from actor; clients provide version for mutation. Define ProblemDetail, bounded paging, 401/403/404 and 409 as in the consolidated design. Restrict deployed Swagger.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
