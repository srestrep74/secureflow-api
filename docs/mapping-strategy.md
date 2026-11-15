# Mapping Strategy

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Explicitly map HTTP records to application commands, application results to responses, and aggregates to persistence models. A security adapter translates Spring identity to an immutable application actor. No generic mapping framework or decorative ACL.

Persistence mappers explicitly reconstruct aggregates and value objects, and copy mutable state onto managed JPA entities. PostgreSQL timestamps store microseconds; separate bounded nanosecond remainders preserve the full Java `Instant` precision. Identity and creation time are checked on update. Mappers do not assign Hibernate lock versions.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
