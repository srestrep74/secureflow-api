# Bounded Contexts

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Identity owns users, credentials, roles and sessions. Task owns tasks and immutable owner IDs. A role is a named grant bundle; a permission is an action and scope; an authority is its Spring representation. Cross-module contracts are application-facing.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
