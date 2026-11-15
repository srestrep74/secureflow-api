# Technology compatibility evidence

**Status: Phase 1 build and integration verification recorded below.** Evidence consulted 2026-11-15. Online pages may move to newer patch versions; recheck before future upgrades.

Phase 1 verification on 2026-11-15: Amazon Corretto 25.0.3, Gradle 9.9.1 and Spring Boot 4.0.9 resolved and compiled; `check` passed with PostgreSQL Testcontainers 2.0.5. The resolved runtime includes Spring Data JPA 4.0.8, Flyway 11.14.1, PostgreSQL JDBC 42.7.14 and springdoc 3.0.3. The integration test verified the OpenAPI endpoint when explicitly enabled. JUnit Jupiter 6.0.3 is Boot-managed. ArchUnit is not included: the current foundation has no domain or application packages to check. Compose startup was not tested. Other historical notes below describe pre-build research.

| Component | Candidate / evidence | Remaining verification |
|---|---|---|
| Java | Java 25 LTS toolchain | Select vendor and current security patch; verify locally |
| Gradle Kotlin DSL | 9.1 or later supports running on Java 25 per [Gradle matrix](https://docs.gradle.org/current/userguide/compatibility.html) | Pin a current stable patched 9.x and Wrapper checksum; verify plugin and OS |
| Spring Boot | 4.0.x: [system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) document Java 25 and Gradle 9.x support | Select a current stable security-patched 4.0.x available at implementation time and verify build |
| Spring Security / Data JPA | Use the versions managed by the selected Boot BOM; [managed coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html) currently list Security 7.0.8 and Data JPA 4.0.8 for Boot 4.0.9 | Check the actual selected patch BOM and end-to-end runtime; no independent override |
| PostgreSQL JDBC / Flyway | The same BOM currently lists PostgreSQL JDBC 42.7.14 and Flyway 11.14.1, including its PostgreSQL database module | Verify chosen PostgreSQL server version and migration execution |
| JUnit / Testcontainers | The same BOM currently lists JUnit Jupiter 6.0.3 and Testcontainers 2.0.5 | Verify test engine, PostgreSQL module, Docker and target OS |
| springdoc / Swagger UI | [springdoc compatibility matrix](https://springdoc.org/faq.html) maps Boot 4.0.x to springdoc 3.0.x; [release list](https://github.com/springdoc/springdoc-openapi/releases) provides available patches | Select a stable 3.0.x compatible with the selected Boot patch and verify generated OpenAPI and UI; UI is supplied transitively |
| ArchUnit | Useful for dependency rules | Exact stable version and Java 25 bytecode support are not verified; confirm before inclusion |

These are a candidate family and observed BOM values, not a claim that every current latest version has been identified or that the entire combination has been tested. Source pages may have advanced beyond the current evaluation date; do not assume a listed patch was available on that date without checking its dated release. Before Phase 1 pinning, verify release dates and advisories, resolve the BOM and run the actual build and relevant tests. This unresolved verification is a Phase 0 readiness limitation.
