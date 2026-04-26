# Architecture

## Build & Compatibility

- **Decision log:** `docs/technical-decision-register.md` (canonical why/rationale)
- **Source code:** Java 21 (use modern language features)
- **Target:** Compile to Java 17 bytecode (`--release 17`)
- **Build tool:** Gradle (multi-module)
- **User codebases:** Accept any Java language level as a parameter (Java 8 through 25+)

## Architecture

- **AST Parser:** JavaParser (`com.github.javaparser:javaparser-core`)
- **Logging:** SLF4J API
- **Entry point:** `Formatter.format(String sourceCode) -> String`
- **Config:** `FormatterConfig` record with builder (`io.princeofspace.model`)

## Package Structure (`:core` / `modules/core`)

| Package | Visibility | Purpose |
|---------|-----------|----------|
| `io.princeofspace` | public | Public API: `Formatter`, `FormatterException` |
| `io.princeofspace.model` | public | Immutable value types: `FormatterConfig`, `IndentStyle`, `WrapStyle`, `JavaLanguageLevel` |
| `io.princeofspace.internal` | internal | All implementation classes (see rules below) |

## Coding Conventions

### Internal package
- All implementation classes live in `io.princeofspace.internal`. ArchUnit tests enforce import boundaries and visibility allowlists for this package.
- Classes in `io.princeofspace.internal` are **package-private** by default. Callers outside the package cannot see them.
- The single exception is the "engine" class that `Formatter` must call across the package boundary — currently `FormattingEngine`. It is `public` but still lives in `internal` as a clear signal that it is not part of the public API.
- Never add `public` to an internal class unless it genuinely needs to be called from a different package.

### Public API cleanliness
- `Formatter` must not import any library-specific types (JavaParser, SLF4J, etc.). It delegates entirely to `FormattingEngine`.
- Public API surfaces (`Formatter`, `FormatterConfig`, enums) must not expose library types in their signatures.

### Value classes
- Prefer `record` for immutable value types. Validation goes in the compact constructor.
- Keep the `Builder` nested inside the record when a multi-field fluent API is needed.

### Tests
- Test classes for package-private classes must be in the **same package** as the class under test (e.g., tests for `io.princeofspace.internal.*` live in `src/test/java/io/princeofspace/internal/`).
- Idempotency is a required suite-level invariant: representative tests (for example corpus/golden/fuzz coverage) must continuously enforce `format(format(x)) == format(x)`.
- Focused behavior tests should add an explicit idempotency assertion when practical for the scenario under test.

## Modules

Gradle projects live under `modules/` (logical names unchanged: `:core`, `:cli`, …).

- `modules/core` (`:core`) — Formatting engine (deps: javaparser, slf4j-api)
- `modules/core-bundled` (`:core-bundled`) — Shaded fat jar (no transitive deps)
- `modules/spotless` (`:spotless`) — Spotless FormatterStep integration
- `modules/cli` (`:cli`) — Command-line tool (picocli); `--java-version N` creates `JavaLanguageLevel.of(N)` which is translated to a JavaParser `LanguageLevel` inside `FormattingEngine` when parsing
- `modules/intellij-plugin` (`:intellij-plugin`) — IntelliJ Platform plugin: **Settings → Tools → Prince of Space** persists full `FormatterConfig` (workspace-scoped), optional format-on-save, and language level from the module or a fixed release; **Code → Reformat with Prince of Space…** uses the same configuration. Develop with `./gradlew :intellij-plugin:runIde`, package with `./gradlew :intellij-plugin:buildPlugin`
- `modules/vscode-extension/` — VS Code extension (Node/TypeScript; **not** a Gradle subproject): registers a **Java document formatting** provider and **Prince of Space: Format Document**; runs `java -jar` on the **CLI shadow JAR** (`:cli:shadowJar`), resolving `modules/cli/build/libs/prince-of-space-cli-*.jar` from the workspace unless `princeOfSpace.cliJar` is set

### `core` vs `core-bundled`

| Artifact | When to use |
|----------|-------------|
| **`io.github.agustafson:prince-of-space-core`** (normal POM) | Default for Gradle/Maven apps: small footprint; you resolve JavaParser and SLF4J alongside the formatter (or rely on your existing versions where compatible). |
| **`io.github.agustafson:prince-of-space-bundled`** (single JAR, classifier default) | Classloader-sensitive embeds, tools that must not pull transitives, or environments where pinning one fat JAR is simpler than managing dependency alignment. Third-party packages are relocated under `io.princeofspace.shaded.*` so they do not clash with host classpath versions. |

**Gradle — `core`:**

```kotlin
dependencies {
    implementation("io.github.agustafson:prince-of-space-core:VERSION")
}
```

**Gradle — `core-bundled` (fat JAR on classpath only):**

```kotlin
dependencies {
    implementation(files("libs/prince-of-space-bundled-VERSION.jar"))
    // or from a repository that publishes the shadow artifact:
    // implementation("io.github.agustafson:prince-of-space-bundled:VERSION")
}
```

Behavior of `io.princeofspace.Formatter` and `FormatterConfig` is the same; only dependency packaging differs.

## Java Language Level

`JavaLanguageLevel` is a first-party record (`io.princeofspace.model`) that decouples the public API from JavaParser's `LanguageLevel` enum:

```java
public record JavaLanguageLevel(int level, boolean preview) implements Serializable
```

- **`level`** — Java feature-release number (e.g. `17`, `21`, `25`). Legacy versions `1`–`7` are supported.
- **`preview`** — `true` to enable preview language features for that release.
- **Factory methods:** `JavaLanguageLevel.of(17)`, `JavaLanguageLevel.of(21, true)`.

Internal translation to JavaParser's `LanguageLevel` happens in `io.princeofspace.internal` when `FormattingEngine` configures the parser:
- Levels 1–7 map via a dedicated switch to `JAVA_1_0` through `JAVA_7`.
- Levels 8+ resolve via `LanguageLevel.valueOf("JAVA_" + level)` (or `"JAVA_" + level + "_PREVIEW"` when `preview` is true).

This design means the public API is stable even when JavaParser adds new enum variants.

## Configuration Options (7 total)

| Option | Default |
|--------|---------|
| `wrapStyle` | `balanced` |
| `indentStyle` | `spaces` |
| `indentSize` | `4` |
| `lineLength` | `120` |
| `closingParenOnNewLine` | `true` |
| `trailingCommas` | `false` |
| `javaLanguageLevel` | `JavaLanguageLevel.of(17)` |

For **`indentSize`**, the numeric value is a count of **spaces** when using spaces, or a count of **tab characters** when using tabs (`docs/formatting-rules.md`, §1 and §3). Continuation indent for delimited list continuations is always `2 * indentSize` (not configurable). Wrapped method chains are an exception and use a single `indentSize` step (see TDR-015).

## Pipeline (`FormattingEngine`)

Parse → `LexicalPreservingPrinter.setup` (comment/token coherence for transforms) → `BraceEnforcer` / `AnnotationArranger` → `PrettyPrinter` (comments and Javadoc enabled) → `BlankLineNormalizer`.

## Testing

- Golden file tests: `examples/inputs/` → format → compare with `examples/outputs/` (all 24 showroom goldens asserted in CI via `./gradlew :core:test`); see `docs/showroom-scenarios.md` for how scenarios map to Java levels
- Whenever formatting behavior changes, add/update a corresponding showroom scenario in `examples/inputs/`
  and regenerate `examples/outputs/` to keep examples as a developer-facing regression artifact.
- Wrapping regressions: `WrappingFormattingTest` (method chains, logical AND, `implements` wrapping)
- Comment preservation: `CommentPreservationTest` (line, block, Javadoc, EOL, between statements, type-use)
- Idempotency fuzz: `IdempotencyFuzzTest` (randomized `FormatterConfig` over fixed snippets + AST-built CU)
- Examples corpus: `ExamplesCorpusFormatTest` (outputs idempotent; inputs single-pass)
- 4 Java levels (java8, java17, java21, java25) × 6 config combinations = 24 golden files
- 200+ total tests in `core` (unit/integration + showroom goldens + corpus checks; optional tests may be skipped)
- Idempotency must be enforced by dedicated test coverage (for example fuzz/corpus/golden suites): `format(format(x)) == format(x)`
