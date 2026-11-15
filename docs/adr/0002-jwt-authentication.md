# ADR-0002: JWT Authentication

**Status: Accepted.** Approved by the user on 2026-11-15; implementation remains scheduled for a later phase.

Alternatives: custom filter, standard Resource Server with local issuer, full authorization server. Recommend Resource Server and RS256 with trusted keys and explicit validators. Custom parsing increases security risk; full IAM is excessive. AuthenticationManager and JWT infrastructure stay in adapters. Online checks are specified by ADR-0004.

See the [consolidated Phase 0 design](../phase-0-design.md) for the complete contract, trade-offs and approval gate.
