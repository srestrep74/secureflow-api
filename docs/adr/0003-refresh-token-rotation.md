# ADR-0003: Refresh Rotation

**Status: Accepted.** Approved by the user on 2026-11-15; rotation and replay policy remain scheduled for a later phase.

Alternatives: long-lived JWT, stateful signed refresh JWT, opaque random credential. Recommend hashed opaque 256-bit secrets, family lock and one-time rotation. Strict replay revokes family and commits before denial. Legitimate concurrent attempts or lost responses force reauthentication; clients single-flight. Grace periods trade security for usability. See the full protocol and retention in the consolidated design.

Atomic consumption and successor creation guarantee at most one rotation; family revocation on a subsequent presentation is a separate strict replay policy. Redis atomic commands or locks cannot distinguish a legitimate concurrent retry from theft or atomically commit PostgreSQL state.

See the [consolidated Phase 0 design](../phase-0-design.md) for the complete contract, trade-offs and approval gate.
