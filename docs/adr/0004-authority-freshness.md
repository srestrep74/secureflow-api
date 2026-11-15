# ADR-0004: PostgreSQL Security State and Authority Freshness

**Status: Accepted.** Approved by the user on 2026-11-15; implementation remains scheduled for a later phase.

The catalog version is retained for the approved design: although mappings are fixed within a release, changes across releases and mixed deployments can otherwise pair old grants with a current user version. If deployments guarantee immutable mappings and prevent mixed versions, this mechanism may be reconsidered through a separately approved ADR change. Phase 1 does not implement it.

## Context and alternatives

A signed JWT cannot reflect later role removal, account disablement or session revocation. Self-contained short-lived JWTs avoid online reads but accept stale access. An online PostgreSQL check costs a read and depends on database availability but gives a clear post-commit boundary. Hybrid checks add divergent enforcement. Redis introduces invalidation races and another failure domain.

## Recommendation

PostgreSQL primary is authoritative for active user, monotonic user security version, active family and durable catalog version. JWTs carry server-issued signed authority snapshots and the user and catalog versions used to create them. After cryptographic verification, each protected request calls `SecurityStatePort` with verified subject and family ID. The PostgreSQL adapter obtains a minimal immutable projection in one indexed, parameterized statement, without aggregate hydration or a separate authority query. Validate account, versions, family ownership, status and expiry before converting the snapshot to authorities. Reject bad or stale credentials with 401; return 503 on state-store failure.

Effective role assignment/removal, status changes, credential resets and forced global logout increment user version atomically with the change; no-op updates do not. Session-specific logout and replay revoke only the family. Catalog mapping changes increment the durable catalog version in a migration. Login and refresh read current roles and versions consistently under the user lock, then mint a signed snapshot. The binary must understand the DB catalog version; coordinate deployments to prevent mixed versions granting stale permissions. The request's read is its linearization point; already-authorized requests may complete later. Sensitive administrative writes lock and recheck their actor in their transaction.

## Consequences and verification

An indexed primary-DB read increases latency and makes availability dependent on PostgreSQL. The port permits adapter replacement but its consistency and failure contract remains binding. Test query count, projection, indexes and execution plan under representative data; changes to roles and catalog, no-ops, logout, disabled users, concurrent issuance and role changes, and fail-closed DB errors. Exercise real signed tokens and the security filter chain. See [complete contract](../phase-0-design.md#security-state-and-authentication).

## Redis as a future option

No Redis in the MVP. Any future proposal must specify maximum staleness, durable invalidation and ordering/fencing, stale-fill and commit/invalidation races, eviction, failover, restart, partition and DB fallback, plus durability, monitoring and operational costs. TTL and asynchronous invalidation permit stale grants; synchronous invalidation alone cannot atomically commit with PostgreSQL. If freshness cannot be guaranteed, consult the primary DB or fail closed. Redis requires a separately reviewed architectural decision.
