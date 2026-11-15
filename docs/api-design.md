# API Design

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Use versioned JSON over HTTPS. Register, login, refresh and logout under /api/v1/auth, plus /me, task and administrative resources. Derive task owner from actor; clients provide version for mutation. Define ProblemDetail, bounded paging, 401/403/404 and 409 as in the consolidated design. Restrict deployed Swagger.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

## Registration HTTP contract

`POST /api/v1/auth/register` is anonymous and accepts JSON with only `username`, `email`, and `password`. Unknown fields are rejected. Username and email follow the domain rules and are returned normalized. The password is preserved exactly, requires 12–128 Unicode code points, at most 512 UTF-8 bytes, valid Unicode, and cannot be blank. The request body is limited to 4096 bytes. A successful request returns `201` with `id`, `username`, and `email`. There is no Location header because a user retrieval endpoint is not yet implemented. Role, status, identifiers and security metadata are server controlled. No credential is returned.

Errors use Problem Details with generic descriptions: malformed or invalid input is 400, a known duplicate username or email is 409, and unexpected failures are 500. Reliably identified infrastructure unavailability is 503. Duplicate responses allow account enumeration; this is an accepted consequence of the approved conflict contract and should be revisited alongside rate limiting and abuse controls before deployment. OpenAPI and Swagger UI are disabled by default; JSON requires explicit enablement and the UI requires both settings to be enabled.
