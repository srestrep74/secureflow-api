# SecureFlow API — Codex Engineering Contract

## 1. Purpose and scope

This file contains **durable instructions** for Codex working in this repository. The product specification lives in `initial-prompt.md`; do not copy the entire product specification into this file. The repository is an educational but production-minded Java backend: a modular monolith implementing authentication, authorization, and task management using tactical DDD, hexagonal architecture, and Spring Security.

Treat security and correctness as non-negotiable. Prefer a small, explicit, testable design over elaborate abstractions. Challenge unnecessary complexity; do not silently replace an approved decision.

## 2. Project context and source-of-truth order

Before changing code, read only the files relevant to the task, using this routing guide:

1. `AGENTS.md`: permanent working and engineering rules.
2. `docs/project-status.md`: actual phase, blockers, verification, and next steps.
3. `docs/implementation-plan.md`: phase goals and acceptance gates.
4. `docs/adr/`: **Accepted** ADRs govern specific architecture decisions; a **Proposed** ADR does not.
5. Relevant topical documentation (`docs/architecture.md`, `docs/domain-model.md`, `docs/authorization-model.md`, etc.).
6. `initial-prompt.md`: original product intent and long-term learning goals.
7. Existing implementation and passing tests: evidence of current behavior, not proof that the behavior is correct.

When documents conflict, flag the conflict. Do not silently treat an unapproved proposal as accepted. A newer approved ADR supersedes the corresponding older proposal. If repository state differs from documentation, inspect and report the drift; update only when the task warrants it.

## 3. Operating procedure

- Start by stating the task scope, affected boundaries, important risks, and a short implementation plan in Spanish.
- Inspect existing conventions before introducing types, packages, or dependencies.
- Work in the **currently approved phase**. Phase 0 is design-only until explicitly approved. Do not start application implementation simply because an initial prompt describes future phases.
- Work incrementally; keep changes cohesive and reviewable. Avoid unrelated refactors, speculative functionality, and unrequested infrastructure.
- For nontrivial design changes, compare viable options, explain trade-offs, and request explicit approval before replacing an Accepted ADR or crossing a phase gate.
- At completion: run applicable checks, report actual results and limitations, update project status and phase checklists, and propose any ADR changes. Never claim a test passed if it was not executed.
- Answer and mentor in **Spanish**. Use **English** for source identifiers, API field names, permission codes, git commits, technical documentation, and code.

## 4. Architecture invariants

- Follow modular-monolith boundaries, tactical DDD, ports and adapters, Clean Architecture, and SOLID **pragmatically**.
- Domain code must not import Spring Framework, Spring Security, Servlet/Jakarta Web, JPA/Hibernate, HTTP DTOs, or infrastructure classes.
- Application services orchestrate use cases and transaction boundaries; business invariants belong to domain entities, value objects, and domain policies.
- Inbound HTTP adapters handle transport/validation/mapping, not business decisions. Outbound adapters implement application ports.
- Dependency direction: adapters/configuration -> application -> domain. The domain must not depend on adapters.
- Use package-by-capability first; avoid a single global `service`, `repository`, or `util` package.
- Distinguish domain aggregates/entities from JPA entities and transport DTOs. Map explicitly across boundaries; do not leak JPA entities into API responses.
- Use an ACL only for a **real model translation boundary**, such as mapping Spring Security identity to `AuthenticatedActor`. Do not create ornamental ACL layers.
- Model aggregates, value objects, domain services, policies, events, factories, and ports only where a concrete rule or dependency warrants them. Avoid one interface per class and unnecessary base classes.
- Cross-module communication must respect public application contracts; prevent cyclic dependencies.

## 5. Authentication and authorization rules

- Deny by default. Public endpoints must be explicitly allowlisted. Enforce authorization on the backend, including object-level ownership, not only controller URL checks.
- Distinguish role names (`USER`, `AUDITOR`, `ADMIN`), Spring role authorities (`ROLE_USER`, etc.), and fine-grained permission authorities (`task:read:own`, `task:read:any`, etc.). Centralize mapping; never sprinkle security strings through arbitrary classes.
- Registration must not accept elevated roles or permissions from clients. Administrative role/status changes require explicitly guarded use cases.
- Use Spring Security **OAuth2 Resource Server** to authenticate Bearer JWTs when feasible. Prefer standard `BearerTokenAuthenticationFilter`, `JwtAuthenticationProvider`, `JwtDecoder`, and `JwtAuthenticationConverter` over a custom JWT filter. Username/password login may use `AuthenticationManager` and `DaoAuthenticationProvider`.
- Validate JWT signature with an allowlisted algorithm and trusted key, issuer, audience, expiry, not-before (where relevant), and token purpose. Do not trust unverified claims. Use sufficiently strong asymmetric keys; do not commit real private keys.
- Prefer short-lived JWT access tokens and opaque high-entropy refresh tokens stored **hashed** with rotation/reuse detection and transaction-safe revocation. Resolve permissions freshness and account-disabling policy explicitly before shipping.
- Never store current authenticated user, JWT, or `Authentication` in mutable fields of singleton services. Use an appropriate request/security-context boundary through a `CurrentActor` port.
- Avoid leaking token values, passwords, PII, signing keys, or refresh tokens to logs, exceptions, Swagger examples, or responses.
- Use `AuthenticationEntryPoint` for unauthenticated requests (typically 401) and `AccessDeniedHandler` for forbidden requests (typically 403). Do not assume CORS authenticates or authorizes.
- CORS must have explicit allowed origins in deployed environments. Define CSRF policy based on **how credentials are sent**, not on JWT presence alone. Do not disable security checks merely to make a test pass.
- Treat IDOR/BOLA, token replay, brute-force login, privilege escalation, inactive accounts, and concurrency as first-class attack scenarios.

## 6. Java and code conventions

- Target Java 25 using a compatible, verified Spring Boot/Spring Security/Gradle toolchain. Use **Gradle Kotlin DSL** and the checked-in Gradle Wrapper, not Maven.
- Apply SOLID without mechanical over-abstraction. Favor explicit dependencies via constructor injection; keep Spring beans stateless unless documented concurrency handling requires otherwise.
- Use meaningful, consistent names and small cohesive methods. Avoid catch-all utilities, magic values, hidden side effects, and deeply nested conditionals.
- Prefer records for immutable API DTOs, commands, results, and value objects. Use sealed types, pattern matching, switch expressions, Optional, Streams, and lambdas only when they clarify the model.
- Avoid `Optional` fields in JPA entities and `Optional` parameters; use it mainly for potentially absent return values.
- Do not write **source-code comments**, `TODO` comments, or explanatory JavaDoc. Express intent through names, types, contracts, tests, OpenAPI metadata, and documentation. Preserve legally required license notices; generated/configuration material may require tool-mandated directives.
- No Lombok initially. No new generic mapping framework unless explicitly justified. Do not write custom cryptography.
- Externalized configuration must use validated, typed configuration properties rather than repeated literals.

## 7. Data and persistence

- Use PostgreSQL, Spring Data JPA, and versioned Flyway migrations. Avoid `ddl-auto=update` as a migration strategy.
- JPA entities belong to persistence adapters. Enforce DB constraints, uniqueness, appropriate indexes, ownership foreign keys, and optimistic locking where justified.
- Avoid N+1 queries, careless cascading, default-EAGER assumptions, unbounded result sets, and reliance on Open Session in View for business logic.
- Transaction boundaries belong at relevant application use cases. Refresh rotation must be atomic and concurrency-safe.
- DTO/command -> application/domain and domain -> persistence model mappings must be explicit and appropriately located. Only create external outbound DTOs for actual external transport contracts.

## 8. HTTP and documentation

- Design versioned `/api/v1` endpoints with clear resource names, input validation, proper statuses, pagination, and consistent errors (`ProblemDetail`/RFC 9457 when appropriate).
- Use OpenAPI 3 / Swagger UI with compatible dependencies. Define the Bearer security scheme, security requirements per route, request/response examples without secrets, error schemas, and validation constraints.
- Do not return entities or internal exception details over HTTP.
- Keep architecture narratives, diagrams, policies, and decisions in `docs/`, not source-code comments.

## 9. Tests and verification

- Every behavior change needs proportionate verification. Write unit tests for invariants and policies; integration tests for JPA, migrations, and refresh-session transactions; Spring Security tests for JWT/authz; architecture tests for dependency direction.
- Required negative cases include: unauthenticated 401, unauthorized 403, invalid/expired/wrong-issuer/wrong-audience JWT, nonowner modification, role escalation, revoked/reused refresh token, inactive account according to policy, and concurrent requests without principal leakage.
- Prefer PostgreSQL Testcontainers for persistence integration tests. Use mocks at boundaries, not to simulate away security behavior being tested.
- When Gradle Wrapper is available, use relevant commands such as `./gradlew test`, `./gradlew check`, and `./gradlew bootRun`. On Windows use `gradlew.bat`. Do not invent tasks; inspect `./gradlew tasks` when needed.
- If the project is still in design phase, state that tests/build are not yet applicable.
- When tools, Docker, dependencies, or credentials are unavailable, report the limitation accurately and identify unverified behavior.

## 10. Code review rules

Flag as security or architecture blockers:

- Mutable authenticated-user data in singleton services; bypassed role or ownership checks; role assignment from public signup; unvalidated JWT issuer/audience/signature; hardcoded production secrets; refresh token stored raw; accidental token logs.
- Controller-to-JPA coupling; Spring/JPA imports in domain; leaking JPA entities to REST; circular bounded-context dependencies; using ACL purely as a rename for a mapper.
- Broad `permitAll`, disabled CSRF without a credential-transport rationale, wildcard credentialed CORS, exceptions swallowed, missing negative tests, or failing validation hidden in reports.
- Accidental introduction of microservices, Kafka, Redis, Keycloak, generic CQRS/event buses, or other out-of-scope infrastructure.

Review for correctness and exploitability first, then tests, maintainability, and style. Report issues with paths, concrete impacts, and an actionable correction.

## 11. Architecture and phase governance

- `docs/adr/` is the record of decisions. Draft documents and **Proposed** ADRs are options, not binding approvals.
- Update `docs/project-status.md` with dates, phase state, verified tests, blockers, and next steps; never fabricate accomplishments.
- Update `docs/implementation-plan.md` only as requirements/checklists are actually met.
- Create or update an ADR for material choices, including authentication token format, authz source/freshness, bounded-context boundaries, or security model changes.
- After the user approves Phase 0, reflect the approved outcome in topical docs and ADRs and update the project status before coding.
- When new sessions start, re-read this file and relevant status/ADRs; do not rely on chat history as the sole memory.

## 12. Initial repository state

The provided `docs/` files are **design drafts**. No implementation or Phase 0 approval is implied by their presence. Inspect the filesystem instead of assuming the app, Gradle Wrapper, migrations, or tests already exist. The pre-existing `initial-prompt.md` belongs to the user and must not be overwritten or replaced.

## 13. Final response format for every implementation task

In Spanish, summarize: **scope completed**, **key decisions and trade-offs**, **files changed**, **verification actually executed and results**, **risks/open questions**, **documents updated**, and **recommended next action**. Keep it concise and evidence-based.

## Git Workflow and Conventional Commits

All repository changes must follow a professional Git workflow using Conventional Commits.

### Mandatory commit policy

- Every completed and successfully verified implementation task must be committed.
- Every completed phase must have all its approved changes committed before proceeding to the next phase.
- Do not commit incomplete implementations, failing tests, or unverified changes as completed work.
- Do not create empty commits when a phase is complete but all its changes are already committed.
- Documentation-only tasks must also use Conventional Commits.
- Commit messages must always be written in English.
- Never automatically push commits to a remote repository unless explicitly requested.
- Never amend, rebase, reset, or force-push existing history without explicit authorization.

### Conventional Commit format

Use the following format:

`<type>(<scope>): <description>`

Allowed types:

- `feat`: New functionality.
- `fix`: Bug fixes.
- `refactor`: Internal restructuring without behavior changes.
- `test`: Test additions or modifications.
- `docs`: Documentation changes.
- `build`: Gradle, dependencies, and build configuration.
- `chore`: Repository maintenance and tooling.
- `ci`: Continuous integration configuration.
- `perf`: Performance improvements.
- `security`: Security hardening, when used as a project-specific convention.

Use standard Conventional Commit types whenever possible.

### Commit message standards

- Use lowercase types and scopes.
- Use the imperative mood.
- Keep the subject concise, ideally under 72 characters.
- Describe the actual change, not merely the phase number.
- Use a descriptive scope such as `domain`, `security`, `identity`, `task`, `persistence`, `api`, `bootstrap`, or `docs`.
- Use a commit body when the change requires additional context.
- Do not include unrelated changes in the same commit.

Examples:

- `chore(bootstrap): initialize Spring Boot project foundation`
- `feat(domain): implement user and task aggregates`
- `feat(identity): implement credential authentication`
- `feat(security): add JWT authentication and validation`
- `feat(auth): implement refresh token rotation`
- `fix(security): prevent unauthorized task access`
- `test(domain): cover aggregate invariants`
- `docs(architecture): document authentication decisions`

### Required workflow before committing

After completing a task or phase:

1. Review the current Git status and diff.
2. Verify that the implementation satisfies its acceptance criteria.
3. Execute the relevant tests and Gradle verification tasks.
4. Confirm that no secrets, credentials, private keys, generated files, or sensitive environment configuration will be committed.
5. Update relevant architectural documentation, implementation progress, and project status.
6. Stage only files belonging to the completed work.
7. Create a descriptive Conventional Commit.
8. Verify that the commit was created successfully.
9. Report the commit hash and commit message.
10. Report any remaining uncommitted changes.

### Failure handling

If compilation, tests, architecture checks, or security verification fail:

- Do not commit the task as completed.
- Investigate and resolve the failure.
- If blocked, report the blocker and leave the changes uncommitted.
- Never bypass verification simply to create a commit.

If unrelated user changes already exist:

- Do not overwrite them.
- Do not silently include them in your commit.
- Stage and commit only files related to the current task.

### Project continuity

This Git workflow applies to every future Codex session.

Before starting a new phase, review:

- `AGENTS.md`
- `docs/project-status.md`
- `docs/implementation-plan.md`
- Recent Git history
- Current Git working tree status

Use Git history and project documentation together to recover the current development state.

A task or phase is only considered fully delivered when its implementation, required verification, documentation updates, and corresponding commit have been completed successfully.

## Code Formatting and Style Enforcement

SecureFlow API enforces consistent source-code formatting using Spotless and Palantir Java Format.

Formatting is a mandatory engineering quality gate for every implementation task and development phase.
Documentation-only changes require checks appropriate to their content; unrelated application integration tests are not required unless build or executable behavior changes.

### Formatting standards

- Use Spotless as the primary formatting and verification tool.
- Use Palantir Java Format for all Java source files.
- Follow the formatter's standard indentation, line wrapping, spacing, and layout conventions.
- Remove unused Java imports through the configured formatter.
- Keep source files free of trailing whitespace.
- Require a final newline.
- Apply the same formatting rules to production and test code.
- Do not introduce additional conflicting Java formatters.
- Do not disable formatting checks to make a build pass.
- Do not introduce formatter suppression directives without explicit approval.
- Formatting must not alter application behavior.
- Formatting does not replace SOLID, Clean Code, security reviews, or static analysis.

### Mandatory workflow before every commit

After completing an implementation task or phase, execute these steps in order:

1. Review the implementation and the relevant Git diff.
2. Execute `.\gradlew.bat spotlessApply`.
3. Execute `.\gradlew.bat spotlessCheck`.
4. Execute `.\gradlew.bat clean check --no-daemon`.
5. Investigate and resolve any formatting, compilation, or test failures.
6. Review the resulting diff to ensure the formatter introduced no unintended changes.
7. Update the relevant documentation.
8. Stage only files belonging to the completed work.
9. Create an English Conventional Commit.
10. Report the verification results and commit hash.

Never commit implementation changes as completed work if `spotlessCheck` or the required verification tasks fail.

### Existing code and formatting changes

- Format existing repository Java code when initially introducing Spotless.
- Keep the initial formatting baseline separate from unrelated functional changes.
- Avoid unnecessary repository-wide formatting during later feature work.
- Do not manually undo formatting produced by the approved formatter.
- If formatting introduces a compilation issue, investigate the root cause rather than disabling the formatter.

### Future Codex sessions

These rules apply permanently to every future Codex session.

Before committing any implementation task or marking a phase complete, Codex must run the formatting and verification workflow.

A task or phase is not fully delivered until formatting, tests, documentation, and Conventional Commit requirements have been satisfied.
