# API Design

**Current status:** Phase 5 is functionally complete and approved; Phase 6 is not implemented. Phase-specific descriptions below record the state at that phase unless they explicitly describe the current implementation. See [project status](project-status.md) and [the closure audit](phase-5-security-audit.md).

**Historical baseline:** Phase 0 was approved on 2026-11-15; later phases have separate approval gates.

Use versioned JSON over HTTPS. Register, login, refresh and logout under /api/v1/auth, plus /me, task and administrative resources. Derive task owner from actor; clients provide version for mutation. Define ProblemDetail, bounded paging, 401/403/404 and 409 as in the consolidated design. Restrict deployed Swagger.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

## Registration HTTP contract

`POST /api/v1/auth/register` is anonymous and accepts JSON with only `username`, `email`, and `password`. Unknown fields are rejected. Username and email follow the domain rules and are returned normalized. The password is preserved exactly, requires 12–128 Unicode code points, at most 512 UTF-8 bytes, valid Unicode, and cannot be blank. The request body is limited to 4096 bytes. A successful request returns `201` with `id`, `username`, and `email`. There is no Location header because a user retrieval endpoint is not yet implemented. Role, status, identifiers and security metadata are server controlled. No credential is returned.

Errors use Problem Details with generic descriptions: malformed or invalid input is 400, a known duplicate username or email is 409, and unexpected failures are 500. Reliably identified infrastructure unavailability is 503. Duplicate responses allow account enumeration; this is an accepted consequence of the approved conflict contract and should be revisited alongside rate limiting and abuse controls before deployment. OpenAPI and Swagger UI are disabled by default; JSON requires explicit enablement and the UI requires both settings to be enabled.

## Phase 5.3B login

When `secureflow.jwt.enabled=true` and valid external RSA keys, issuer, audience and key ID are configured, `POST /api/v1/auth/login` accepts only `identifier` (username or email) and `password`. The JSON body is limited to 4096 bytes; passwords are never normalized. Successful responses contain `access_token`, `refresh_token`, `token_type` (`Bearer`) and `expires_in` (currently 300 seconds), with `Cache-Control: no-store` and `Pragma: no-cache`. The snake_case response explicitly follows the Phase 5.3B contract, superseding the camelCase example in the earlier consolidated design. No authentication cookies are used. Invalid structure returns 400, unsupported content types 415, rejected credentials 401, classified dependency unavailability 503 and unexpected failures 500. Errors do not return credentials. Enforce HTTPS at deployment. OpenAPI remains disabled by default.

## Phase 5.4B HTTP bearer integration

With JWT enabled, GET /api/v1/me requires a valid, current Bearer access token and returns JSON containing only the authenticated user ID in `id`. Invalid or missing authentication returns 401 with a Bearer challenge; insufficient authority returns 403; security-state unavailability returns 503. Responses use safe Problem Details. Other application routes remain denied unless explicitly allowlisted.

## Phase 5.5B refresh

With JWT enabled, POST /api/v1/auth/refresh accepts only JSON refresh_token (maximum 256 characters; body maximum 4096 bytes). Success returns the login token fields with no-store and no-cache headers. Invalid structure yields 400, unsupported media 415, invalid credentials 401, classified dependency unavailability 503 and unexpected failures 500. No access JWT is needed. Use HTTPS and omit Authorization: an expired Bearer header is rejected before the controller. Current-family logout is implemented as described below.

## Phase 5.6B: HTTP current-session logout

`POST /api/v1/auth/logout` requires a valid Bearer access JWT and no request body. It returns 204 with an empty body and `Cache-Control: no-store` only after the current family revocation commits. The authenticated JWT supplies the user and family identifiers; clients cannot select another family. Other sessions remain active. The client must discard its locally stored access and refresh tokens. A repeated HTTP logout with the revoked JWT returns 401, although the underlying application operation is idempotent. Invalid authentication or state returns 401 with Bearer semantics, classified infrastructure outages return 503, and unexpected failures return 500 without internal details. Documentation remains opt-in.
