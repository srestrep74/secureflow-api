# SecureFlow API

SecureFlow API is an educational Java backend for learning how Spring Security works in a production-minded modular monolith. It follows a request from the servlet security filters through credential authentication, JWT validation, PostgreSQL security state and authorization. The project also explores DDD, SOLID, hexagonal architecture, transaction boundaries and database concurrency.

Phase 5 is functionally complete, verified, audited and approved. The authentication lifecycle is implemented; the Phase 6 task and administration APIs have not been implemented. Production hardening is intentionally deferred: this repository is a learning reference, not a production-ready deployment.

## Technology

- Java 25 and the checked-in Gradle 9.9.1 Wrapper, using Gradle Kotlin DSL.
- Spring Boot 4.0.9 with Spring Security, OAuth2 Resource Server, Spring Data JPA and Flyway, with versions managed by the Spring Boot BOM.
- PostgreSQL 17 for the local Compose service and integration tests; JDBC for security/session state.
- springdoc OpenAPI 3.0.3; JUnit, PostgreSQL Testcontainers and ArchUnit 1.4.1 for testing.
- Spotless 8.11.0 and Palantir Java Format 2.103.0.

See [technology compatibility evidence](docs/technology-versions.md) and the actual [Gradle build](build.gradle.kts) for version details.

## Architecture

The application is a modular monolith organized by capability. Domain packages hold business models and invariants without Spring or persistence dependencies. Application packages coordinate use cases and define outbound ports. Infrastructure implements those ports with JPA or JDBC, configures Spring Security and adapts verified identities; web adapters validate and map HTTP requests. The identity and task modules are present, but task HTTP operations remain future work.

The security chain uses `AuthenticationManager`, `DaoAuthenticationProvider` and `UserDetailsService` for credentials, and Spring Security OAuth2 Resource Server for Bearer tokens. A signed JWT becomes an authenticated identity only after current PostgreSQL account, session and catalog state is checked; the security context and authorities then support authorization. See [architecture](docs/architecture.md), [authentication flow](docs/authentication-flow.md) and [JWT validation](docs/jwt-validation-flow.md).

## Implemented capabilities

- Registration assigns the USER role and stores passwords as Argon2id hashes. A guarded operator command bootstraps an administrator without public role assignment.
- Credential authentication creates a persistent session family and issues a five-minute RS256 access JWT with validated claims. JWT support is explicitly enabled and requires external RSA keys.
- Protected requests check current PostgreSQL user, session and authorization catalog state. Each family has an absolute 30-day lifetime.
- Opaque refresh credentials are stored as SHA-256 digests. Rotation is atomic; reuse of a consumed, unexpired token revokes its family after a successful commit. Logout revokes only the current authenticated family.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Public | Register a USER account |
| `POST /api/v1/auth/login` | Public, JWT enabled | Authenticate and receive access and refresh credentials |
| `POST /api/v1/auth/refresh` | Public, JWT enabled | Rotate an opaque refresh credential |
| `POST /api/v1/auth/logout` | Bearer JWT, JWT enabled | Revoke the current session family |
| `GET /api/v1/me` | Bearer JWT, JWT enabled | Return the current user ID |
| `GET /actuator/health` | Public | Health status |

Other routes are denied by default. Omit an expired or invalid Bearer header on public endpoints: Resource Server processes it before the controller. OpenAPI and Swagger UI are disabled by default; see [API design](docs/api-design.md). Administrator bootstrap is a local operator command, not an HTTP endpoint; its procedure is in [security decisions](docs/security-decisions.md).

## Local setup

Install Java 25, Docker with Compose, and OpenSSL. The example below uses PowerShell and PostgreSQL Compose on `127.0.0.1:5432`. Choose development-only database credentials when prompted; the commands do not write an `.env` file.

```powershell
$env:POSTGRES_DB = 'secureflow'
$env:POSTGRES_USER = 'secureflow_local'
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Local database password' -AsSecureString)).Password
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/secureflow'
$env:DATABASE_USERNAME = $env:POSTGRES_USER
$env:DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
docker compose up -d
```

Create development-only RSA keys outside this repository, in a directory accessible only to your user. Configure filesystem permissions appropriate to your OS before generating the private key. The key must be unencrypted PKCS#8 PEM, the public key X.509 PEM, and both must match; the minimum RSA size is 2048 bits.

```powershell
$keyDir = Join-Path $env:USERPROFILE 'secureflow-dev-keys'
New-Item -ItemType Directory -Path $keyDir -Force | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out (Join-Path $keyDir 'private.pem')
openssl pkey -in (Join-Path $keyDir 'private.pem') -pubout -out (Join-Path $keyDir 'public.pem')
$env:SECUREFLOW_JWT_ENABLED = 'true'
$env:SECUREFLOW_JWT_PRIVATEKEY = Join-Path $keyDir 'private.pem'
$env:SECUREFLOW_JWT_PUBLICKEY = Join-Path $keyDir 'public.pem'
$env:SECUREFLOW_JWT_KEYID = 'local-dev'
$env:SECUREFLOW_JWT_ISSUER = 'https://secureflow.local'
$env:SECUREFLOW_JWT_AUDIENCE = 'secureflow-api'
.\gradlew.bat bootRun
```

Wait for PostgreSQL to become healthy before starting the application. Flyway applies versioned migrations at application startup, and Hibernate validates the schema. JWT-disabled mode supports registration and health but does not expose login, refresh, logout or `/api/v1/me`. Never commit keys, environment credentials or issued tokens. For Unix-like systems use `./gradlew` instead of `gradlew.bat` and set equivalent environment variables.

## Verification

A running Docker daemon is required for the PostgreSQL 17 Testcontainers tests; the tests use isolated databases. JUnit covers domain and application behavior; integration tests exercise persistence, security filters, HTTP, PostgreSQL outages and transactional concurrency. ArchUnit checks dependency boundaries. Run:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat clean check --no-daemon
```

The Phase 5 closure audit recorded 150 tests with no failures or skips. Tests provide confidence in the scenarios executed, not proof of deployment readiness. See the [security audit](docs/phase-5-security-audit.md), [testing strategy](docs/testing-strategy.md), [implementation plan](docs/implementation-plan.md), and [production hardening backlog](docs/production-hardening-backlog.md).
