# SecureFlow API

Phase 1 bootstrap; subsequent functionality requires approval. See [project status](docs/project-status.md) and [architecture](docs/phase-0-design.md).

Requires Java 25, Docker with Compose and the checked-in Gradle Wrapper. Set `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` in the environment, then run `docker compose up -d`. Set `DATABASE_URL` to `jdbc:postgresql://localhost:5432/<database>`, and set `DATABASE_USERNAME` and `DATABASE_PASSWORD` to the same database credentials. Avoid committing credentials or `.env` files.

On Windows run `./gradlew.bat bootRun` and `./gradlew.bat check`; on Unix run `./gradlew bootRun` and `./gradlew check`. Tests require a running Docker daemon for PostgreSQL Testcontainers. Actuator exposes only `/actuator/health`; OpenAPI endpoints are disabled until explicitly configured in a later phase.
