# JWT Validation

**Current status:** Phase 5 is functionally complete and approved; Phase 6 is not implemented. Phase-specific descriptions below record the state at that phase unless they explicitly describe the current implementation. See [project status](project-status.md) and [the closure audit](phase-5-security-audit.md).

Phase 5.1B initially implemented structural validation only; the current system also performs Resource Server and PostgreSQL security-state validation. Enabling JWT requires `secureflow.jwt.issuer` and `secureflow.jwt.audience` in addition to the external RSA key properties. These trusted values are operator configuration, never selected from the token. The configured public key and `kid` are the only trusted key; RS256 is the only accepted algorithm.

The protected header is `alg: RS256`, `kid` equal to the configured key ID and `typ: JWT`. The signed payload requires `iss`, `aud` (a nonempty string or string array containing the configured audience), `sub`, `jti`, `sid`, `iat`, `nbf`, `exp`, `token_use: access`, `security_version`, `catalog_version`, `roles` and `permissions`. Identifiers are canonical lowercase UUID strings. Versions are JSON integers: security version is nonnegative; the supported catalog version is 1. Roles are a nonempty array of unique `ROLE_USER`, `ROLE_AUDITOR` or `ROLE_ADMIN` strings. Permissions are a unique string array exactly matching the union of the approved grants for those roles. No email or secret is included. The fixed catalog is implemented in the JWT infrastructure and must remain coordinated with the durable catalog initialized by Flyway V6.

The issuer receives an existing user UUID, session-family UUID, versions and roles; it cannot create a session. It generates a fresh `jti`, takes UTC time from a clock and signs with `NimbusJwtEncoder`. The issued-at and not-before values are the same second, and expiration is exactly five minutes later. Tokens have no public issuing endpoint.

`NimbusJwtDecoder` verifies the signature and algorithm; its composed validators enforce the trusted key ID, issuer, audience, mandatory expiration and not-before, and the project-specific claims. Timestamps must be numeric dates, expiration must be after issuance, not-before cannot be after expiration, issued-at cannot exceed the current time by more than 30 seconds, and declared lifetime cannot exceed five minutes. Spring's timestamp validator applies the approved 30-second skew to expiration and not-before. The skew permits acceptance shortly outside the nominal window but does not extend the declared lifetime. Invalid types, absent claims, unsupported versions, unknown roles or permissions and mismatched snapshots fail closed.

A verified token is not an authorization decision. The current Resource Server validates account status, session ownership and revocation, and user and catalog versions against PostgreSQL through `SecurityStatePort`; stale or unavailable state never falls back to signed claims.

The `sid` claim identifies the persisted family. Flyway V6 initializes catalog version 1, which must agree with the compiled catalog; login, refresh and protected requests enforce compatibility. Deployment must coordinate catalog migration and binaries.

## Phase 5.3A issuance

Login signs an RS256 access token inside the session-creation transaction using the locked user's current roles and security version and the persisted family ID as `sid`. Permissions come from the server catalog. The existing issuer generates the unique `jti`, issuer, audience, purpose and five-minute lifetime. The current implementation checks the persisted catalog version before issuance and validates PostgreSQL state on protected requests. Signing or commit failure prevents a successful application result and rolls back session creation.

## Phase 5.4A: authoritative security state

`SecurityStatePort` retrieves an immutable, minimal account, family and catalog projection using the verified subject and `sid`. `SecurityStateValidator` accepts framework-independent trusted claims and rejects missing or inactive accounts, absent or foreign families, revoked families, families outside their creation-to-expiry interval, and mismatched user or catalog versions. It does not load roles, credentials or refresh-token history. Invalid state returns false; a query failure or missing/incompatible catalog raises `SecurityStateUnavailableException` with a sanitized message. The current HTTP integration distinguishes invalid state (401) from classified unavailability (503) before trusting JWT authorities.

The PostgreSQL adapter uses one parameterized statement with primary-key lookups and a singleton catalog lookup. PostgreSQL READ COMMITTED supplies one statement snapshot: all projected values reflect the same snapshot, without row locks. A change committed after the query starts may not affect that request, and an already authorized operation may finish after revocation. Stronger guarantees for sensitive writes require transaction-specific locking and rechecks. This read costs one database round trip and a connection; no benchmark or representative query plan has been measured. No cache or aggregate hydration is used.

## Phase 5.4B HTTP bearer integration

NimbusJwtDecoder enforces RS256, the trusted key ID, issuer, audience, lifetime, purpose and required claims. The converter maps the verified subject, session and versions to TrustedSecurityClaims and performs one PostgreSQL state projection before accepting roles and permissions. It creates JwtAuthenticationToken only after successful validation; no successful state is cached. Invalid claims or stale state result in 401; missing or incompatible catalog metadata and database failures result in 503.

## Refresh and current access tokens

Phase 5.5A creates an RS256 access JWT from current roles, user security version and compatible catalog version under the user lock. The existing online state check rejects the family after replay revocation commits. Requests already authorized before revocation may complete. The refresh use case does not require an access JWT.

## Refresh HTTP interaction

Refresh needs no access JWT. Resource Server processes any supplied Bearer header, so an expired token is rejected before the public refresh controller. Omit that header. The new RS256 JWT uses the same family and current server-side authorities; replay revocation causes subsequent family-bound JWT authentication to fail.

## Current-session logout

Phase 5.6A revokes the family in PostgreSQL. Subsequent Resource Server checks reject JWTs referencing that family, while other active families remain valid. A token held by a client is not erased and a request authenticated before commit may finish. No blacklist or cache is used. HTTP logout was added in Phase 5.6B.

Phase 5.6B exposes authenticated current-family revocation at `POST /api/v1/auth/logout`. After commit, each subsequent Bearer request checks PostgreSQL and rejects the revoked family, even though the signed JWT string still exists. Previously authenticated in-flight requests can finish. Other families remain valid.
