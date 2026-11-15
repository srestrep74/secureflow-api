# Authorization

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

USER manages own tasks, AUDITOR reads any, ADMIN manages any and user administration. Effective permissions union assigned roles. Enforce general permission before ownership: 401 invalid identity, 403 no general permission, 404 missing or concealed resource. Own lists are filtered.

Login and refresh derive a signed authority snapshot from current roles and the fixed catalog in a consistent transaction. Effective role changes increment the user security version atomically; catalog grant changes increment the durable catalog version. Protected requests compare both versions before accepting the snapshot. An unchanged assignment is a no-op. Session revocation is checked independently.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
