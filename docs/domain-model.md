# Domain Model

Identity owns `User`, `Username`, `EmailAddress`, `Role` and `AccountStatus`. Registration creates an active user with only USER and security version zero. Effective role and status changes increment that version; repeated requests are no-ops. Username and email are trimmed and canonicalized to lowercase ASCII; uniqueness is a database responsibility. Email uses a pragmatic ASCII syntax. User exposes immutable role snapshots. Credential hashing and storage are deferred; no plaintext or hash is modeled here.

Task owns `Task`, `TaskId`, `TaskTitle` and `TaskStatus`. `UserId` is a neutral shared UUID value; Task references it without a User object. Task starts in TODO with immutable owner and timestamps supplied by its caller. Title is stripped and limited to 200 UTF-16 code units, description is nullable and limited to 4000 UTF-16 code units. Task mutations cannot move modification time backwards. Each effective edit or transition increments its version; no-ops do not. A failed validation leaves state unchanged. Optimistic concurrency requires a conditional database write in Phase 3.

Inputs use `NullPointerException` for missing required arguments, `IllegalArgumentException` for invalid values, and `IllegalStateException` for forbidden transitions; numeric overflow fails with `ArithmeticException`. These are domain contracts, not HTTP responses.

Explicit reconstitution factories validate required state, nonnegative versions and timestamp ordering. Persistence reconstructs value objects through their constructors and does not annotate aggregates with JPA.
