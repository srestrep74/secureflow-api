# Java code style

Spotless Gradle plugin 8.11.0 applies Palantir Java Format 2.103.0 to Java source sets, including production and tests. Palantir's default conventions govern indentation, spacing, wrapping, classes, methods, lambdas and streams. Spotless also removes unused imports, trims trailing whitespace and ensures a final newline. Application source code must remain free of comments as required by AGENTS.md.

Before an implementation commit, run:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat clean check --no-daemon
```

Review the resulting diff before committing. `spotlessApply` rewrites files; `spotlessCheck` only verifies them. Gradle's `check` includes `spotlessCheck` and fails on violations without rewriting files. To fix a formatting failure, run `spotlessApply`, review the changes, and repeat the checks. Documentation-only changes require checks appropriate to their content.
