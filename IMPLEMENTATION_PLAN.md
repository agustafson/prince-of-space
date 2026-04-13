# Prince of Space — Implementation Plan

This plan is designed for AI agents or developers to pick up and execute sequentially. Each phase builds on the previous one. Reference `docs/01-project-priorities.md` for project goals and `docs/02-formatting-decisions.md` for all formatting rules and configuration options.

---

## Phase 0: Project Scaffolding

**Goal:** Set up the multi-module Gradle project structure with build tooling.

### Tasks

1. **Create module `spotless/build.gradle.kts`**:
   - Depends on `core`
   - Dependency on `com.diffplug.spotless:spotless-lib` (compile-only / provided scope)

2. **Create module `cli/build.gradle.kts`**:
   - Depends on `core`
   - Dependency on `info.picocli:picocli` for CLI argument parsing
   - gradle jar plugin or `com.gradleup.shadow` to produce an executable JAR

3. **Create `.editorconfig`** for the project itself (4 spaces, UTF-8, LF line endings).

4. **Create a basic `README.md`** with project name, one-line description, and "under construction" notice.

5. **Verify the build compiles** with `./gradlew clean build`.

---

## Phase 1: Configuration Model ✓ COMPLETE

**Goal:** Define the configuration data model used by the formatter.

### Tasks

1. **Create `FormatterConfig.java`** in `core/src/main/java/io/princeofspace/model/`:
   ```
   - Immutable value class (record or final class with builder)
   - Fields matching docs/02-formatting-decisions.md Part 2:
     - indentStyle: enum IndentStyle { SPACES, TABS }
     - indentSize: int (default 4)
     - preferredLineLength: int (default 120)
     - maxLineLength: int (default 150)
     - continuationIndentSize: int (default 4)
     - wrapStyle: enum WrapStyle { WIDE, NARROW, BALANCED }
     - closingParenOnNewLine: boolean (default true)
     - trailingCommas: boolean (default false)
     - javaLanguageLevel: pass-through value forwarded to JavaParser (no formatter-side range validation)
   - Static method `defaults()` returning default config
   - Builder pattern for constructing custom configs
   - Validation: maxLineLength >= preferredLineLength, indentSize > 0, continuationIndentSize > 0, etc.
   ```

2. **Write unit tests** for config defaults, builder, and validation behavior.

---

## Phase 2: Core Formatting Engine — AST Pipeline ✓ COMPLETE

**Goal:** Build the parse → transform → print pipeline.

### Tasks

1. **Create `Formatter.java`** — the main entry point:
   ```
   public class Formatter {
       public Formatter(FormatterConfig config);
       public String format(String sourceCode);
       public String format(String sourceCode, Path filePath);  // for diagnostics
   }
   ```
   - Parse input using JavaParser with configured language level
   - Apply formatting visitors
   - Print output using custom pretty-printer
   - Guarantee idempotency: `format(format(x)) == format(x)`

2. **Create `FormattingVisitor.java`** — a JavaParser `VoidVisitor` or `ModifierVisitor` that traverses the AST and applies formatting rules. This is the heart of the formatter. Break into sub-visitors or helper classes by concern:

   a. **`BraceEnforcer`** — Adds braces to braceless `if`, `else`, `for`, `while`, `do` bodies.

   b. **`BlankLineNormalizer`** — Enforces blank line rules (one between methods, collapse multiples, none after `{` / before `}`).

   c. **`AnnotationArranger`** — Ensures declaration annotations are one-per-line. Preserves type-use annotation positions.

3. **Create `PrettyPrinter.java`** — custom pretty-printer (extending or replacing JavaParser's `DefaultPrettyPrinter`):
   - This is the most complex component. It controls how each AST node is printed to a string.
   - Must be aware of all 8 configuration options.
   - Key responsibilities:
     - Indentation (tabs/spaces, indent size)
     - Line width tracking (preferred vs max)
     - Continuation indent
     - Wrap style decision logic

4. **Write integration tests** using the example input files from `examples/inputs/` and validating against `examples/outputs/`.

### Acceptance Criteria (docs/02 non-configurable behaviors)

- Forced braces are always added for `if`/`else`/`for`/`while`/`do` single-statement bodies.
- Brace placement is K&R style for supported constructs.
- Declaration annotations are one-per-line; parameter/type-use annotation placement is preserved.
- Import organization remains out of scope for `core` (delegated to Spotless).

---

## Phase 3: Core-Bundled Packaging ✓ COMPLETE

**Goal:** Ship a dependency-free bundled artifact for environments that cannot use transitive dependencies.

### Tasks

1. **Create `core-bundled` module build**:
   - Depends on `core`
   - Produces a shaded/relocated fat JAR with no transitive runtime requirements

2. **Configure relocation and packaging rules**:
   - Relocate third-party packages as needed to avoid classpath conflicts
   - Preserve public formatter API entry points

3. **Add verification tests**:
   - Smoke test formatting through bundled artifact classpath
   - Ensure output parity between `core` and `core-bundled` on golden samples

4. **Document artifact usage**:
   - Describe when to use `core` vs `core-bundled`
   - Include minimal dependency examples

---

## Phase 4: Formatting Rules — Wrapping & Line Breaking (core + showroom goldens aligned)

**Goal:** Implement the line-breaking and wrapping logic, which is the most complex and critical part of the formatter.

**Status:** `PrincePrettyPrinterVisitor` implements width-aware layout (preferred/max, wrap styles, continuation indent) for method chains, parameter/argument lists, binary/`+`/`&&`/`||`, ternaries, `implements`/`permits` clauses, array initializers, and record headers. **Showroom:** `FormatterShowcaseGoldenTest` compares `Formatter.format` on `examples/inputs/.../FormatterShowcase.java` to `examples/outputs/<java8|java17|java21>/`; run `./gradlew :core:showroomGoldenTest`. After intentional formatter changes, refresh goldens with `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens` (see `RegenerateShowroomGoldens`). These tests are tagged `showroom-golden` and excluded from the default `test` task; remove that exclude in `core/build.gradle.kts` to enforce in CI. Try-with-resources column alignment remains follow-up work; see `WrappingFormattingTest` for targeted regressions.

### Tasks

1. **Implement the wrapping decision engine** in `PrettyPrinter`:
   - For each "wrappable" construct (param list, arg list, chain, binary expr, etc.):
     1. Try to print it on a single line
     2. If it exceeds `preferredLineLength`, apply wrapping per `wrapStyle`
     3. If the wrapped result still exceeds `maxLineLength`, force more aggressive wrapping
   - The two-threshold (preferred/max) logic:
     - Between preferred and max: allow if wrapping would produce ugly results (e.g., single-token continuation)
     - Above max: must wrap regardless

2. **Implement construct-specific formatting:**

   a. **Method/constructor parameters and call arguments:**
      - Wrap per `wrapStyle`
      - Apply `closingParenOnNewLine`
      - Use `continuationIndentSize` for wrapped lines

   b. **Method chaining:**
      - Detect chains (sequential method calls on the result of the previous call)
      - If chain doesn't fit on one line, put each `.method()` call on its own line
      - Indent chained calls by `continuationIndentSize` from the chain start
      - Handle lambdas within chains specially (see below)

   c. **Lambda expressions:**
      - Short single-expression lambdas: keep inline
      - Block lambdas: `{` on same line as `->`, body indented, `}` aligned with statement start
      - Lambda as last argument: open inline, never wrap the opening `(` to a new line
      - Lambda within a chain: indent body relative to the chain, not the method call

   d. **Ternary expressions:**
      - If fits on one line: keep
      - Otherwise: `?` and `:` at start of continuation line, indented by `continuationIndentSize`

   e. **Binary operator expressions:**
      - If fits on one line: keep
      - Otherwise: operator at start of continuation line, indented by `continuationIndentSize`
      - Group by precedence where sensible

   f. **String concatenation:**
      - `+` at start of continuation line
      - Follow `wrapStyle` for how aggressively to wrap

   g. **Try-with-resources:**
      - Multiple resources: align declarations vertically to column after `try (`
      - Apply `closingParenOnNewLine` to the closing `)`

   h. **Implements/permits/extends clauses:**
      - Wrap like parameter lists per `wrapStyle`
      - Keep K&R brace placement semantics for the declaration body
      - When a type clause wraps across lines, the opening `{` of the type body follows the same rule as the closing `)` for wrapped parameter lists: if `closingParenOnNewLine` is true, put `{` alone on its own line (after the last type); if false, keep `{` on the same line as the last type (or immediately after it)

   i. **Array initializers:**
      - Wrap per `wrapStyle`
      - Apply `trailingCommas` for multi-line

   j. **Enum constants:**
      - Simple enums that fit: one line
      - Otherwise: one constant per line
      - Enums with bodies: one per line, blank line before members

   k. **Generic type parameters and bounds:**
      - Wrap like parameter lists when they exceed line length

3. **Write comprehensive tests** for each construct, testing all 3 wrap styles and edge cases. Use the `examples/outputs/` files as golden-file tests.

### Acceptance Criteria (docs/02 non-configurable behaviors)

- Lambda argument handling keeps opening `(` inline (never moved to a separate line due to lambda presence).
- Ternary and binary operator wrapping place operators at the start of continuation lines.
- Method chaining uses continuation indentation from chain start (no dot-alignment drift).
- Type clauses (`implements`/`permits`/`extends`) do not use a closing `)`, but when wrapped they use the **`closingParenOnNewLine` setting to style the `{`** that opens the type body (same “delimiter on its own line” idea as wrapped parameters).

---

## Phase 5: Comment Preservation ✓ COMPLETE

**Goal:** Ensure all comments survive formatting unchanged and remain associated with the correct code.

**Status:** After parse, the compilation unit uses JavaParser `LexicalPreservingPrinter.setup` so AST transforms keep comments attached. The pretty-printer enables `PRINT_COMMENTS` and `PRINT_JAVADOC`. See `CommentPreservationTest` for line, block, Javadoc, end-of-line, inter-statement, and type-use-adjacent cases. Further edge cases (exhaustive orphan positions, block-comment reindent tuning) can be added incrementally.

### Tasks

1. **Handle line comments (`//`)**: Preserve position relative to the code they annotate. If a statement is wrapped, the comment stays with its line or moves to above the statement.

2. **Handle block comments (`/* */`)**: Preserve content exactly. Reindent leading whitespace to match new indentation level.

3. **Handle Javadoc (`/** */`)**: Preserve content. Reindent to match new indentation.

4. **Handle inline end-of-line comments**: When a line is wrapped, an end-of-line comment on the original line should stay at the end of the first wrapped line or move to above the statement.

5. **Handle orphan comments**: Comments between statements that JavaParser may not associate with any node. Preserve their position relative to surrounding code.

6. **Write tests** for every comment placement scenario.

### Acceptance Criteria (docs/02 non-configurable behaviors)

- Line, block, Javadoc, and orphan comments are preserved with stable association.
- Type-use annotations remain adjacent to the annotated type and are not moved across modifiers.

---

## Phase 6: Idempotency & Determinism

**Goal:** Guarantee that formatting is stable and repeatable.

### Tasks

1. **Implement idempotency check** in the test suite: for every test case, verify `format(format(input)) == format(input)`.

2. **Implement determinism check**: verify that the same input always produces the same output regardless of OS, locale, or JVM version.

3. **Fuzz testing**: Generate random valid Java files (using JavaParser's AST construction API) and verify idempotency holds.

4. **Performance benchmarks**: Format large open-source Java projects (e.g., Spring Framework, Guava) and measure:
   - Time per file
   - Total time for project
   - Memory usage
   - Compare against google-java-format

---

## Phase 7: Spotless Integration

**Goal:** Create a Spotless `FormatterStep` so users can integrate via Spotless's Maven/Gradle plugins.

### Tasks

1. **Create `PrinceOfSpaceStep.java`** in the `spotless` module:
   ```
   - Implement Spotless's FormatterStep interface
   - Method: String format(String rawUnix, File file)
   - Load FormatterConfig from the project (or accept config via Spotless DSL)
   - Instantiate Formatter and delegate
   ```

2. **Handle classpath isolation**: Spotless loads formatters in isolated classloaders. Ensure our step works with `JarState` + reflection, or provide a compile-only integration.

3. **Create usage documentation** showing how to configure in both `build.gradle` and `pom.xml`:
   ```groovy
   // Gradle example
   spotless {
       java {
           custom 'princeOfSpace', { source ->
               // or, once first-class:
               // princeOfSpace()
           }
       }
   }
   ```

4. **Write integration tests** that use Spotless's test harness (`StepHarness`) to verify formatting through the Spotless pipeline.

---

## Phase 8: CLI Tool

**Goal:** Standalone command-line tool for formatting files.

### Tasks

1. **Create `Main.java`** in the `cli` module using Picocli:
   ```
   prince-of-space [OPTIONS] [FILES...]

   Options:
     --check          Check if files are formatted (exit 1 if not)
     --config FILE    Path to config file
     --java-version N Java language level (default: 17)
     --stdin          Read from stdin, write to stdout
     -r, --recursive  Recursively format .java files in directories
     -v, --verbose    Verbose output
   ```

2. **Implement file discovery**: Given directories, find all `.java` files. Respect `.gitignore` patterns.

3. **Implement `--check` mode**: Report which files would change without modifying them. Exit code 0 if all formatted, 1 if any would change.

4. **Implement parallel formatting**: Use virtual threads (Java 21) to format multiple files concurrently.

5. **Write tests** for CLI argument parsing and file discovery.

---

## Phase 9: Testing Against Real-World Code

**Goal:** Validate the formatter against large, real-world Java codebases.

### Tasks

1. **Create a test harness** that:
   - Clones a list of well-known Java projects (Spring Boot, Guava, Apache Commons, etc.)
   - Formats every `.java` file
   - Verifies no parse errors occur
   - Verifies idempotency
   - Reports statistics (files formatted, time taken, lines changed)

2. **Fix any issues** discovered by real-world testing. This will likely surface edge cases in:
   - Comment preservation
   - Complex generic type expressions
   - Annotation processing
   - Unusual but valid Java syntax

3. **Collect before/after samples** for documentation and marketing.

---

## Phase 10: Configuration Loading (Deferred)

**Goal:** Add optional project-level config loading after formatter behavior is stable.

### Tasks

1. **Create `FormatterConfigLoader.java`**:
   - Load config from `.princeofspace.toml` (and/or `.princeofspace.yaml`) in project root
   - Fall back to `FormatterConfig.defaults()` when config file is absent
   - Support optional environment variable overrides (e.g., `PRINCE_OF_SPACE_MAX_LINE_LENGTH`)
   - Keep parser lightweight and startup overhead minimal

2. **Integrate loader into CLI and Spotless entry points**:
   - `core` API remains configuration-object driven (no hidden global state)
   - Loader is an integration convenience layer

3. **Write tests** for file discovery, parsing, defaults fallback, and env overrides.

---

## Phase 11: IDE Plugins (Future)

**Goal:** IDE integration for IntelliJ IDEA and VS Code.

### Tasks (deferred — design only for now)

1. **IntelliJ Plugin**: Use the IntelliJ Platform SDK to create a formatting action. Delegate to `core` or `core-bundled`. Register as a code style provider.

2. **VS Code Extension**: Create a VS Code extension that invokes the CLI tool or uses a language server. Register as a document formatting provider.

These are lower priority and can be tackled after the core formatter is stable.

---

## Dependency Summary

| Module | Dependencies |
|--------|-------------|
| `core` | `javaparser-core`, `slf4j-api` |
| `core-bundled` | `core` (shaded) |
| `spotless` | `core`, `spotless-lib` (provided) |
| `cli` | `core`, `picocli`, `slf4j-simple` |

## Key Design Principles

1. **The `Formatter` class is the single entry point.** Everything flows through `format(String) -> String`.
2. **Configuration is immutable.** Create a `FormatterConfig`, pass it to `Formatter`, done.
3. **The pretty-printer is the hard part.** Phases 2-3 are where most of the complexity lives. Invest heavily in tests here.
4. **Test against golden files.** The `examples/outputs/` directory provides 36 golden files across 3 Java levels and 12 config combinations. Use these as the primary test suite.
5. **Idempotency is non-negotiable.** Every test must verify `format(format(x)) == format(x)`.
6. **Comments are sacred.** Never lose, reorder, or corrupt a comment.

## Non-Configurable Rule Acceptance Criteria

The following rules from `docs/02-formatting-decisions.md` must be asserted by tests and treated as release gates:

1. **Forced braces:** always add braces for braceless control-flow bodies.
2. **K&R braces:** opening brace stays on the same line for supported constructs.
3. **Lambda behavior:** lambda argument formatting never pushes opening `(` to its own line.
4. **Operator continuation style:** wrapped ternary (`?`, `:`) and binary operators lead continuation lines.
5. **Method chains:** continuation indent from chain start; avoid dot-alignment drift.
6. **Annotation safety:** preserve type-use annotation placement and keep declaration annotations one-per-line.
7. **Imports out of scope:** import sorting/grouping/removal is delegated to Spotless.

## Execution Order

```
Phase 0 (scaffolding) → Phase 1 (config model) → Phase 2 (AST pipeline) → Phase 3 (core-bundled)
→ Phase 4 (wrapping) → Phase 5 (comments) → Phase 6 (idempotency) → Phase 7 (Spotless)
→ Phase 8 (CLI) → Phase 9 (real-world testing) → Phase 10 (config loading deferred)
→ Phase 11 (IDE plugins)
```

Phases 0-1 can be completed quickly. Phases 2-5 are core formatter work and will take the longest. Phase 6 runs continuously alongside 2-5. Phases 7-8 are integration work. Phase 9 is validation. Phase 10 is intentionally deferred for operational convenience features. Phase 11 is future work.
