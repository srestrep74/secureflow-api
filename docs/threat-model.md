# Threat Model

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Credential abuse requires rate limits and generic login errors; forgery requires actual JWT validation; IDOR requires backend ownership policy; stale grants require online state; refresh theft requires rotation and family revocation; last-admin races require serialization. Protect keys and tokens, test database outage and secret redaction, and reassess before deployment.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
