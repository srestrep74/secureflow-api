# ADR-0005: Administrator Bootstrap

**Status: Accepted.** Approved by the user on 2026-11-15; implementation remains scheduled for a later phase.

Alternatives: public endpoint, default credentials, privileged operator bootstrap. Recommend a one-shot operator command targeting an existing active account; no default credentials. A durable guard row serializes bootstrap and all changes affecting active administrator count. Reject removal of the last active ADMIN. Operational recovery must be separately controlled.

See the [consolidated Phase 0 design](../phase-0-design.md) for the complete contract, trade-offs and approval gate.
