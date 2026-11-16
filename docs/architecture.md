# Architecture

**Current status:** Phase 5 is functionally complete and approved; Phase 6 is not implemented. Phase-specific descriptions below record the state at that phase unless they explicitly describe the current implementation. See [project status](project-status.md) and [the closure audit](phase-5-security-audit.md).

**Historical baseline:** Phase 0 was approved on 2026-11-15; later phases have separate approval gates.

One Spring MVC deployment contains Identity and Task modules. Adapters depend on application and domain, never the reverse. Separate JPA, domain and API models. The security adapter translates verified framework identity into an application actor; domain receives domain facts.

Phase 3 adds repository ports in each module's `application` package and JPA entities, Spring Data repositories, explicit mappers and adapters in `infrastructure`. Each adapter operation joins an existing transaction or starts one; future application use cases must own transactions spanning multiple operations. Reads map within the transaction, with roles fetched explicitly. Open Session in View is disabled.

The current model uses `identity.domain`, `task.domain`, and a minimal `shared.domain.UserId` value shared by both contexts. Neither context depends on the other; architecture tests enforce inward dependencies.

`SecurityStatePort` exposes a minimal immutable user/version/family/catalog projection after JWT verification; its PostgreSQL adapter performs one indexed primary-database statement without loading aggregates. The token contains signed authority snapshots bound to user and catalog versions. Redis is excluded from the MVP; a future cache must preserve or explicitly revise the freshness and failure contract in ADR-0004.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.3 adds an application-owned credential authentication port and identity result. Spring Security types, the principal, authority conversion and the provider manager remain in Identity infrastructure; application and domain do not depend on the framework. Current roles are read for credential authentication, while later Phase 5 work established token issuance consistency.

Phase 4.4 runs an explicit `bootstrap-admin` command in a non-web Spring context. The application service defines the transaction; a PostgreSQL adapter locks the durable singleton guard row before reading the user and administrator count. The user aggregate assigns ADMIN and the existing repository persists it using optimistic locking. The guard is operational coordination, not a domain aggregate. Future operations changing active administrator count must follow the same lock order and transaction protocol (ADR-0005).

Phase 5.2 adds an Identity application session persistence port and a JDBC adapter, consistent with existing credential storage. A single transactional adapter operation inserts the family and initial digest; database foreign keys and uniqueness protect consistency. The session is a technical consistency root, not a separate bounded context. At Phase 5.2, HTTP endpoints and online JWT checks had not yet been added.

Phase 5.4A adds application-owned `SecurityStatePort`, immutable claims and snapshot records, and a framework-independent validator. A JDBC outbound adapter projects account status/version, session ownership/revocation/timestamps and the global catalog version in a single indexed PostgreSQL statement. It returns no entities or secrets. A single READ COMMITTED statement observes one snapshot; concurrent changes committed after its start are not guaranteed to affect that request. Database errors and missing or incompatible catalog metadata fail closed as a sanitized availability exception. Bearer authentication was added in Phase 5.4B.

## Phase 5.5A application refresh

RefreshSessionUseCase depends on application-owned session, user, security-state, token and transaction ports. The PostgreSQL adapter performs indexed resolution and user-then-family row locks; Spring transaction infrastructure commits or rolls back before returning the outcome. Signing uses the existing access-token issuer. The refresh HTTP adapter was added in Phase 5.5B.
