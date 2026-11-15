# Aggregate Design

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

User and Task protect local invariants; PostgreSQL constraints protect cross-record integrity. Task uses optimistic version. Refresh family uses row locking. The last-admin invariant requires a serialized guard row.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
