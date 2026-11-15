# JWT Validation

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

Resource Server validates signature, algorithm, issuer, audience, expiry and purpose. After verification, one `SecurityStatePort` call reads the indexed user/version/family/catalog projection. Compare token versions and family ownership/status before mapping the signed authority snapshot. There is no per-request role or aggregate fetch. Invalid or revoked token yields 401, database unavailability yields 503. See ADR-0002 and ADR-0004.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
