# ADR-0001: Modular Monolith

**Status: Accepted.** Approved by the user on 2026-11-15 for Phase 0 and Phase 1 bootstrap.

Alternatives: technical-layer CRUD or microservices. Recommend capability modules in one deployment with inward dependencies and explicit mapping. CRUD reduces mapping but weakens boundaries; microservices add operational cost. Verify boundaries with architecture tests.

See the [consolidated Phase 0 design](../phase-0-design.md) for the complete contract, trade-offs and approval gate.
