# Domain Model

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

User and Task are aggregate roots. PostgreSQL enforces username/email uniqueness. Task enforces its transitions and immutable owner. Fixed role catalog is not an aggregate. Refresh family is a technical consistency boundary. Domain never depends on application actor.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
