Ã¯Â»Â¿# Security Decisions

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Accepted decisions specify Argon2id, RS256, five-minute access JWT, online security and session state, opaque refresh rotation, explicit HTTPS transport and deny-by-default authorization. PostgreSQL unavailability must fail closed. Online validation and the complete token contract remain scheduled for later phases.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.2B uses a stateless security filter chain with explicit registration and health allowlists and deny-by-default for all other routes. Form login, HTTP Basic, logout and request caching are disabled. CSRF is disabled because the API does not use browser cookies or session authentication and future credentials are explicitly supplied in Bearer authorization headers; reassess this decision if credential transport changes. No cross-origin access is enabled; any future CORS policy must explicitly identify trusted origins. OpenAPI remains disabled by default. A duplicate response reveals that an identifier is registered; this privacy trade-off follows the approved 409 contract and requires abuse controls before production.

Phase 4.3 uses the standard DAO provider and the existing Argon2id encoder, with one explicit ProviderManager and no HTTP session. Credential authentication errors do not disclose whether an account exists. Missing credential records and infrastructure failures are distinct from bad credentials. Passwords are not normalized and credential-bearing command string representations are redacted. Authentication results do not include credentials. Login rate limiting and production password-hashing benchmarks remain required before deployment.

## Administrator bootstrap operations (ADR-0005)

Only trusted deployment operators with permission to run the application and access the primary PostgreSQL database may perform bootstrap. Restrict shell, container execution, deployment configuration, database credentials, direct SQL and access to logs through operating-system and deployment controls. Never supply passwords or database credentials as command arguments. There are no default administrator credentials and normal startup does not run the command.

1. Register the intended user using `POST /api/v1/auth/register` and retain the returned user UUID. Use an approved secure transport and an independently chosen password; the account must remain active and have its persisted credential.
2. Build the executable artifact with `./gradlew bootJar` (Windows: `gradlew.bat bootJar`). Supply the same trusted primary-database configuration used by the application through `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` using the deployment secret mechanism.
3. In a protected execution environment with Java 25 selected on `PATH`, run `java -jar build/libs/secureflow-api-0.0.1-SNAPSHOT.jar bootstrap-admin <user-uuid>`. This starts a non-web context and exits after the transaction completes. Exit 0 means promotion committed or the selected user was already the sole active administrator; exit 1 means failure and exit 2 means invalid arguments. No password is requested by the command.
4. Verify the outcome using an access-controlled read-only database query joining `users` and `user_roles` by user UUID, checking status `ACTIVE` and role `ADMIN`. Do not expose credential hashes. There is no administrative HTTP verification endpoint yet.

A missing or inactive account, missing or invalid credential, an existing different active ADMIN, malformed arguments, unavailable database, or persistence failure prevents promotion. Repeating for the sole active administrator changes nothing. Investigate failures with restricted database and deployment diagnostics; retry only after confirming the intended user and administrative state. If the existing administrator is unavailable, follow a separately authorized recovery procedure; bootstrap is not a general administrative tool. Direct database modification bypasses the guard protocol and must be restricted. The last-active-administrator rule requires future management operations to acquire the same guard before reading or mutating administrative state.

## Phase 5.1A RSA key configuration

JWT cryptographic beans are disabled unless `SECUREFLOW_JWT_ENABLED=true` is explicitly set. This preserves registration-only and operator contexts; every deployment that uses JWTs must explicitly enable them. An invalid boolean, missing keys or key identifier, invalid PEM, nonmatching keys, or RSA modulus shorter than 2048 bits fails startup. There are no fallback keys and no automatic key generation. Configure filesystem paths with `SECUREFLOW_JWT_PRIVATEKEY` and `SECUREFLOW_JWT_PUBLICKEY`, and a stable identifier with `SECUREFLOW_JWT_KEYID` (1â€“128 ASCII letters, digits, `.`, `_` or `-`). Restrict the unencrypted private key file to the application identity; supply it via the deployment secret mechanism and never commit it. The private key must be unencrypted PKCS#8 PEM (`PRIVATE KEY`); the public key must be X.509 SubjectPublicKeyInfo PEM (`PUBLIC KEY`). The `kid` identifies the single currently trusted pair; overlapping rotation is not implemented.

For local development, create the files outside the repository in a directory accessible only to the developer, using OpenSSL:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
```

Set the three key properties to the resulting absolute paths and an identifier, and set the enabled property to `true`; restrict file permissions using the operating system. Development keys must not be used in production. Production must provision a persistent key pair securely and coordinate key replacement; replacing it immediately invalidates tokens signed with the previous key. Issuance from login and online-state validation remain unimplemented.

Phase 5.1B additionally requires `secureflow.jwt.issuer` and `secureflow.jwt.audience` when enabled. Set them to deployment-controlled trusted values shared by issuer and verifier. Tokens use a five-minute lifetime and the approved maximum 30-second clock tolerance; synchronization of host clocks remains an operational requirement. Structural validation is implemented, but the PostgreSQL authority freshness and revocation checks are not. No token issuance is exposed over HTTP.

Phase 5.2 uses a 256-bit random opaque secret and SHA-256 digest, not a refresh JWT. Passwords require Argon2id because human choices have low entropy; randomly generated refresh credentials resist guessing without an expensive password hash. Never log or persist raw secrets or expose digests. Database history retains consumed records; cleanup is not yet implemented.

## Transactional login (Phase 5.3A)

Credential verification precedes a new transaction that locks and reloads the user, persists the family and SHA-256 refresh digest, and signs the JWT before commit. The application returns credentials only after commit. Commands and results redact secrets in their string representations. The locking contract requires all security-state mutations to update the user row atomically with their changes; direct role-table writes that bypass that contract are unsupported. Database commit cannot guarantee delivery of a future HTTP response; expiration bounds abandoned sessions and a later cleanup mechanism is required. No online JWT state validation or durable catalog version exists yet.

## HTTP login operational requirements

JWT login is opt-in and requires external valid RSA key configuration; there is no fallback key. TLS must be enforced at the deployment boundary. Avoid recording request or response bodies, credentials and tokens in access logs or traces. Rate limiting and other login-abuse controls remain required before production deployment. OpenAPI must be explicitly enabled and restricted in production. Token responses use no-store and no-cache headers; no authentication cookie or HTTP session is created.

## Security state and catalog deployment (5.4A)

Flyway V6 creates a positive, unique singleton authorization catalog version initialized to 1. The database value represents the compiled `JwtAuthorityCatalog` definition; deployments must change the mapping and version together through a controlled migration. A missing row or a version incompatible with the running binary fails closed and logs a generic operational error. Existing tokens with a different catalog version are rejected. Old binaries cannot validate against a new version; coordinate rollout and expect temporary unavailability for mixed versions. Restrict database update privileges operationally; no administrative HTTP endpoint is provided.

User security version increments for effective role assignment/removal and account status changes, invalidating old authorization snapshots. The JPA lock version detects concurrent persistence writes and is distinct from that security version; the global catalog version identifies the compiled mapping and is independent of users. Direct SQL mutations bypass aggregate semantics and must not be used for user security changes. Session family revocation invalidates that family only; expiry is checked independently of refresh token consumption.

## Phase 5.4B HTTP bearer integration

The Resource Server is enabled only with the existing JWT activation and valid external keys. It uses Spring Security Bearer extraction and a request-scoped context without HTTP sessions. PostgreSQL is required for each decoded Bearer attempt; no cached-success fallback exists. The READ COMMITTED projection cannot retroactively stop a request already in progress. Classified unavailability fails closed with 503.

## Phase 5.5A rotation and replay

Opaque 256-bit refresh credentials are persisted only as SHA-256 digests. The unique digest index supports bounded lookup; the partial unique index enforces one unconsumed token per family. User then family row locks serialize all compliant writers across application instances. This order also serializes separate families of one user; lock waits and throughput have not been measured. Consumed history is retained. No cache or process-local lock participates. Strict replay revokes a known unexpired family even for simultaneous legitimate retries or a lost response. Typed outcomes allow revocation to commit without an unchecked exception rolling it back; failures during consumption, insertion, signing or commit roll back and fail closed. A database outage was not separately exercised for refresh. Direct SQL writers that bypass the locking protocol remain unsupported.

## Refresh deployment considerations

Require HTTPS in deployment. Provide login and refresh abuse protection and rate limiting, redact credentials in logs and tracing, retain and clean refresh history, plan overlapping RSA key rotation, ensure PostgreSQL availability, and monitor abnormal refresh failures and suspected replay. These controls are not established by the HTTP adapter; deployment transport security has not been verified.

## Current-session logout

Logout uses the existing user-then-family PostgreSQL row-lock protocol and family revocation. Idempotent repeated revocation preserves the first timestamp without updating the row. Invalid context and infrastructure failure are distinct application outcomes. Session-specific revocation does not change account security version or roles; direct writers that bypass the protocol remain unsupported. The endpoint and principal extraction remain Phase 5.6B.

Phase 5.6B keeps Spring Security's default logout disabled and exposes an explicitly authenticated MVC endpoint. It performs persistent family revocation rather than relying on request-context clearing; it creates no HTTP session or authentication cookie. Failures are sanitized: invalid state is 401, classified dependency unavailability 503, and unexpected failures 500. This does not provide logout from all devices, token erasure on clients, or retroactive cancellation of already authenticated requests.

## Phase 5 closure audit

Login locks and verifies the durable authorization catalog before creating a family or issuing credentials. An incompatible catalog produces 503 and no session. Credential authentication distinguishes classified persistence unavailability from unexpected internal failures. See [Phase 5 security audit](phase-5-security-audit.md) for tested outage boundaries, residual transaction gaps and deployment coordination.
