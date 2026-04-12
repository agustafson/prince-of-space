## Build & Compatibility

- **Source code:** Java 21 (use modern language features)
- **Target:** Compile to Java 17 bytecode (`--release 17`)
- **Build tool:** Gradle (multi-module)
- **User codebases:** Accept any Java language level as a parameter (Java 8 through 25+)

## Architecture

- **AST Parser:** JavaParser (`com.github.javaparser:javaparser-core`)
- **Logging:** SLF4J API
- **Entry point:** `Formatter.format(String sourceCode) -> String`
- **Config:** `FormatterConfig` record with builder (`io.princeofspace.model`)

## Package Structure (`core` module)

| Package | Visibility | Purpose |
|---------|-----------|---------|
| `io.princeofspace` | public | Public API: `Formatter`, `FormatterException` |
| `io.princeofspace.model` | public | Immutable value types: `FormatterConfig`, `IndentStyle`, `WrapStyle` |
| `io.princeofspace.internal` | internal | All implementation classes (see rules below) |

## Coding Conventions

### Internal package
- All implementation classes live in `io.princeofspace.internal`. This is a runtime convention; the module system is not yet configured to enforce it.
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
- Every test must verify idempotency where applicable: `format(format(x)) == format(x)`.

## Modules

- `core` — Formatting engine (deps: javaparser, slf4j-api)
- `core-bundled` — Shaded fat jar (no transitive deps)
- `spotless` — Spotless FormatterStep integration
- `cli` — Command-line tool (picocli)

### `core` vs `core-bundled`

| Artifact | When to use |
|----------|-------------|
| **`io.princeofspace:prince-of-space-core`** (normal POM) | Default for Gradle/Maven apps: small footprint; you resolve JavaParser and SLF4J alongside the formatter (or rely on your existing versions where compatible). |
| **`io.princeofspace:prince-of-space-bundled`** (single JAR, classifier default) | Classloader-sensitive embeds, tools that must not pull transitives, or environments where pinning one fat JAR is simpler than managing dependency alignment. Third-party packages are relocated under `io.princeofspace.shaded.*` so they do not clash with host classpath versions. |

**Gradle — `core`:**

```kotlin
dependencies {
    implementation("io.princeofspace:prince-of-space-core:VERSION")
}
```

**Gradle — `core-bundled` (fat JAR on classpath only):**

```kotlin
dependencies {
    implementation(files("libs/prince-of-space-bundled-VERSION.jar"))
    // or from a repository that publishes the shadow artifact:
    // implementation("io.princeofspace:prince-of-space-bundled:VERSION")
}
```

Behavior of `io.princeofspace.Formatter` and `FormatterConfig` is the same; only dependency packaging differs.

## Configuration Options (8 total)

| Option | Default |
|--------|---------|
| `indentStyle` | `spaces` |
| `indentSize` | `4` |
| `preferredLineLength` | `120` |
| `maxLineLength` | `150` |
| `continuationIndentSize` | `4` |
| `wrapStyle` | `balanced` |
| `closingParenOnNewLine` | `true` |
| `trailingCommas` | `false` |

## Testing

- Golden file tests: `examples/inputs/` → format → compare with `examples/outputs/`
- 3 Java levels (java8, java17, java21) × 12 config combinations = 36 golden files
- Every test must verify idempotency: `format(format(x)) == format(x)`
