ï»¿# Security Decisions

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

JWT cryptographic beans are disabled unless `SECUREFLOW_JWT_ENABLED=true` is explicitly set. This preserves registration-only and operator contexts; every deployment that uses JWTs must explicitly enable them. An invalid boolean, missing keys or key identifier, invalid PEM, nonmatching keys, or RSA modulus shorter than 2048 bits fails startup. There are no fallback keys and no automatic key generation. Configure filesystem paths with `SECUREFLOW_JWT_PRIVATEKEY` and `SECUREFLOW_JWT_PUBLICKEY`, and a stable identifier with `SECUREFLOW_JWT_KEYID` (1–128 ASCII letters, digits, `.`, `_` or `-`). Restrict the unencrypted private key file to the application identity; supply it via the deployment secret mechanism and never commit it. The private key must be unencrypted PKCS#8 PEM (`PRIVATE KEY`); the public key must be X.509 SubjectPublicKeyInfo PEM (`PUBLIC KEY`). The `kid` identifies the single currently trusted pair; overlapping rotation is not implemented.

For local development, create the files outside the repository in a directory accessible only to the developer, using OpenSSL:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
```

Set the three key properties to the resulting absolute paths and an identifier, and set the enabled property to `true`; restrict file permissions using the operating system. Development keys must not be used in production. Production must provision a persistent key pair securely and coordinate key replacement; replacing it immediately invalidates tokens signed with the previous key. Neither issuance from login nor the complete claims and online-state validation exists yet.
