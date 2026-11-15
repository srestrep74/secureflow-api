# Architecture

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

One Spring MVC deployment contains Identity and Task modules. Adapters depend on application and domain, never the reverse. Separate JPA, domain and API models. The security adapter translates verified framework identity into an application actor; domain receives domain facts.

Phase 3 adds repository ports in each module's `application` package and JPA entities, Spring Data repositories, explicit mappers and adapters in `infrastructure`. Each adapter operation joins an existing transaction or starts one; future application use cases must own transactions spanning multiple operations. Reads map within the transaction, with roles fetched explicitly. Open Session in View is disabled.

The current model uses `identity.domain`, `task.domain`, and a minimal `shared.domain.UserId` value shared by both contexts. Neither context depends on the other; architecture tests enforce inward dependencies.

`SecurityStatePort` exposes a minimal immutable user/version/family/catalog projection after JWT verification; its PostgreSQL adapter performs one indexed primary-database statement without loading aggregates. The token contains signed authority snapshots bound to user and catalog versions. Redis is excluded from the MVP; a future cache must preserve or explicitly revise the freshness and failure contract in ADR-0004.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

Phase 4.3 adds an application-owned credential authentication port and identity result. Spring Security types, the principal, authority conversion and the provider manager remain in Identity infrastructure; application and domain do not depend on the framework. Current roles are read for credential authentication, while token issuance consistency remains a later-phase responsibility.
