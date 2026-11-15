# JWT Validation

**Status: Phase 5.1A cryptographic infrastructure implemented; complete token validation and HTTP authentication remain future work.**

Resource Server validates signature, algorithm, issuer, audience, expiry and purpose. After verification, one `SecurityStatePort` call reads the indexed user/version/family/catalog projection. Compare token versions and family ownership/status before mapping the signed authority snapshot. There is no per-request role or aggregate fetch. Invalid or revoked token yields 401, database unavailability yields 503. See ADR-0002 and ADR-0004.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

## Phase 5.1A cryptographic boundary

When explicitly enabled, `NimbusJwtEncoder` signs using a configured RSA private key and `NimbusJwtDecoder` verifies with the corresponding public key. The decoder accepts RS256 only, requires the configured `kid`, and applies Spring Security's default timestamp validators when timestamp claims are present. Keys must match and be at least 2048 bits. A valid signature establishes integrity and possession of the private key; it does not establish that claims are complete or currently authorized.

Phase 5.1B must define and require the approved claims and validate issuer, audience, token purpose, subject, session and versions. The decoder in this phase is not integrated with HTTP authentication and must not be used alone to authorize requests. Account state, security and catalog freshness, family revocation and authorization remain subsequent work under ADR-0004.
