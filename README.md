# Prince of Space

A beautiful, configurable Java code formatter.

> **Under Construction** — This project is in active development and not yet ready for use.

## What is this project?

A Java code formatter library that produces beautiful, readable output with a small set of meaningful configuration options. It aims to solve the problems with existing formatters (google-java-format, palantir-java-format, etc.) which are either unconfigurable or produce ugly output.

## Key Documents

- `IMPLEMENTATION_PLAN.md` — Phased implementation plan with detailed tasks
- `docs/01-project-priorities.md` — Project goals, architecture, module structure
- `docs/02-formatting-decisions.md` — All formatting rules, 8 configuration options, and decided behaviors
- `docs/03-research-notes.md` — Research on other formatters, AST parsers, Spotless integration

## Non-goals

- Import organization (delegated to Spotless)
- Maven/Gradle plugins (Spotless provides those)
- Type resolution (not needed for formatting)

## Spotless

The `spotless` module publishes `io.princeofspace.spotless.PrinceOfSpaceStep`, a `com.diffplug.spotless.FormatterStep` that delegates to `io.princeofspace.Formatter`. It is serializable for Spotless classloader isolation.

**Gradle (Kotlin DSL):** register the step inside `spotless { java { ... } }` and ensure the `prince-of-space-spotless` artifact (which pulls in `prince-of-space-core`) is on the classpath used by the Spotless plugin—for example via `buildscript` dependencies, a dedicated `buildSrc` dependency, or your Gradle version’s supported mechanism for extra formatter dependencies. Then:

```kotlin
import io.princeofspace.model.FormatterConfig
import io.princeofspace.spotless.PrinceOfSpaceStep

spotless {
    java {
        target("src/**/*.java")
        addStep(PrinceOfSpaceStep.create(FormatterConfig.defaults()))
    }
}
```

Use `FormatterConfig.builder()` to tune language level, line lengths, and wrap style.

**Maven:** add `prince-of-space-spotless` as a dependency of `spotless-maven-plugin` (see [Spotless Maven](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md) for plugin dependency scope), then configure a custom step that uses `PrinceOfSpaceStep.create(...)` per the plugin’s API for third-party `FormatterStep` implementations.
