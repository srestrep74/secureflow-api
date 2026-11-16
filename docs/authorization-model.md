# Authorization

**Status:** Phase 5 authentication and security-state enforcement are complete and approved. Task and administrative authorization remain Phase 6 work.

USER manages own tasks, AUDITOR reads any, ADMIN manages any and user administration. Effective permissions union assigned roles. Enforce general permission before ownership: 401 invalid identity, 403 no general permission, 404 missing or concealed resource. Own lists are filtered.

Login and refresh derive a signed authority snapshot from current roles and the fixed catalog in a consistent transaction. Effective role changes increment the user security version atomically; catalog grant changes increment the durable catalog version. Protected requests compare both versions before accepting the snapshot. An unchanged assignment is a no-op. Session revocation is checked independently.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.

## Administrator bootstrap and future mutations

ADR-0005 is implemented by a one-shot operator command. It accepts an existing active registered user with a persisted Argon2id credential. While holding the shared database guard, it promotes only when there is no active ADMIN; the sole active ADMIN is an idempotent success. A different active ADMIN causes rejection. USER remains assigned and an effective ADMIN assignment increments the security version once.

The `ActiveAdministratorPolicy` and the shared guard provide the coordination needed by future user administration. Every operation that can change the active ADMIN count must lock the guard first, before loading the target or counting administrators, and hold it through commit: ADMIN assignment or removal, activation or deactivation of an ADMIN, deletion of an ADMIN, and any equivalent bulk or recovery operation. Before removing or deactivating an active ADMIN, check that another active ADMIN exists. No such management use case exists yet: the last-administrator invariant is not enforced against direct SQL or future code that bypasses this protocol. Restrict database writes accordingly.

Phase 5.1B encodes roles as `ROLE_` names and permissions as a separate JSON string array. The single local catalog derives the exact permission union and validates it against the signed role snapshot. Catalog version 1 is the only version this binary understands. The durable catalog and authoritative checks were added in Phase 5.4; signed claims alone are insufficient for authorization.

Phase 5.4A persists the global authorization catalog version in `authorization_catalog_state` and checks it against both trusted token claims and the compiled role-permission catalog. User security versions track effective role and status changes independently. A catalog deployment must coordinate the migration and binaries: old binaries fail closed against a new database version, and tokens carrying an older version are invalid. The mapping itself remains compiled, not dynamically administered.

## Phase 5.4B HTTP bearer integration

After current-state validation, exact validated role names and permissions become GrantedAuthority values; roles are not prefixed again and scopes are not synthesized. GET /api/v1/me requires authentication; unimplemented routes remain denied. An authenticated principal lacking a required authority receives 403. Task and ownership authorization are deferred.
