# Roadmap — Prioritized Tasks

This document lists concrete, actionable tasks for bringing the formatter from its current
state to production readiness. Tasks are ordered by priority. An agent should work through
them sequentially (top to bottom), completing each before moving to the next unless a task
explicitly says it can be parallelised.

**Current state (April 2026):** 109 unit/integration tests + 36 showroom golden tests all
pass. `./gradlew build` is green (Spotless, Checkstyle, SpotBugs, Error Prone, NullAway).
Phases 0–5 of `IMPLEMENTATION_PLAN.md` are complete. The core formatter handles the most
common Java constructs with width-aware wrapping across all 3 wrap styles and 8 config knobs.

---

## Task 1 — Enable golden tests in CI

**Priority:** Immediate (free win)
**Effort:** Tiny

All 36 showroom golden tests pass, but they are excluded from the default `test` task via
`excludeTags("showroom-golden")` in `core/build.gradle.kts`. A formatting regression could
slip in undetected.

### Steps

1. In `core/build.gradle.kts`, remove the `excludeTags("showroom-golden")` line from the
   `tasks.test` block.
2. Run `./gradlew :core:test` — all 145 tests (109 existing + 36 golden) should pass.
3. Run `./gradlew build` — full build stays green.
4. Update the `IMPLEMENTATION_PLAN.md` Phase 4 status paragraph: remove the sentence about
   excluding showroom-golden and enforcing later; state they now run in CI.

### Verification

```
./gradlew :core:test          # 145 tests pass (includes goldens)
./gradlew build               # green
```

---

## Task 2 — Enforce `maxLineLength` (hard limit)

**Priority:** High — this is a headline feature that distinguishes the formatter
**Effort:** Medium-large

### Problem

`docs/02-formatting-decisions.md` says: *"The formatter never exceeds `maxLineLength`."*
The implementation only checks `maxLineLength` in **one** place
(`mustHardWrapChain` in `PrincePrettyPrinterVisitor`). All other wrapping constructs
(parameters, arguments, binary operators, type clauses, array initializers, enum constants)
only check `preferredLineLength`. A line that exceeds `preferred` and is wrapped — but
the wrapped result still exceeds `max` — is not re-broken more aggressively.

### Steps

1. Add failing tests first (TDD). Create test cases in `WrappingFormattingTest` where:
   - `preferredLineLength` is narrow (e.g. 60) and `maxLineLength` is slightly wider (80).
   - A construct wraps at `preferred` but a single wrapped line still exceeds `max`.
   - Assert that the output never has a line longer than `maxLineLength`.
   Constructs to cover: method parameters, call arguments, binary `&&`/`||`, string `+`
   concatenation, `implements` clause (greedy packing), array initializers.
2. Implement a second-pass or fallback in each wrapping helper:
   - After wrapping at `preferredLineLength`, check if any produced line exceeds
     `maxLineLength`.
   - If so, apply more aggressive wrapping (e.g. fall back to one-per-line).
   - A pragmatic approach: in `printBinaryGreedy`, `printGreedyCommaLines`,
     `printParametersList` (WIDE), and `printTypeListGreedy`, add a `budget` parameter
     that can be tightened to `maxLineLength` on a retry.
3. Update golden files if needed (`REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens`).
4. Verify idempotency on every new test case.

### Verification

```
./gradlew :core:test
./gradlew :core:showroomGoldenTest     # if goldens excluded in prior step; otherwise included above
```

### Key files

- `core/src/main/java/io/princeofspace/internal/PrincePrettyPrinterVisitor.java`
- `core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

---

## Task 3 — Implement missing documented formatting features

**Priority:** High — these are features described in docs but not wired up
**Effort:** Medium (three sub-tasks, each small)

### 3a — `trailingCommas` for enum constants

`trailingCommas` is implemented for array initializers (line ~1161 of
`PrincePrettyPrinterVisitor`) but not for enum constants. `docs/02-formatting-decisions.md`
says: *"Java only supports trailing commas in enum constants and array initializers. This
option only affects those contexts."*

**Steps:**
1. Add a failing test: format an enum with `trailingCommas=true`, multi-line output —
   assert the last constant has a trailing comma.
2. In `visit(EnumDeclaration)`, after the last constant in the one-per-line branch, print
   a trailing comma when `fmt.trailingCommas()` is true and the enum wraps to multiple lines.
3. Verify idempotency.

### 3b — Try-with-resources `closingParenOnNewLine`

`docs/02-formatting-decisions.md` says the closing `)` of try-with-resources follows
`closingParenOnNewLine`. The current `visit(TryStmt)` always prints `) ` inline.

**Steps:**
1. Add a failing test: format a try-with-resources with multiple resources and
   `closingParenOnNewLine=true` — assert `)` is on its own line.
2. In `visit(TryStmt)`, after printing the last resource, check
   `fmt.closingParenOnNewLine()` and whether resources wrapped (more than one resource).
   If both true, print a newline before `)`.
3. Update goldens if affected. Verify idempotency.

### 3c — Generic type parameter wrapping

`IMPLEMENTATION_PLAN.md` Phase 4, item 2k says: *"Generic type parameters and bounds:
Wrap like parameter lists when they exceed line length."* No such logic exists —
`printTypeParameters` is inherited from the base class with no width awareness.

**Steps:**
1. Add a failing test: a method or class with a very long generic type parameter list that
   exceeds `preferredLineLength` — assert it wraps.
2. Override `printTypeParameters` in `PrincePrettyPrinterVisitor` with width-aware logic
   analogous to `printParametersList`.
3. Update goldens if affected. Verify idempotency.

### Verification

```
./gradlew :core:test
./gradlew build
```

---

## Task 4 — Implement the Spotless integration

**Priority:** High — primary distribution channel; P1 goal
**Effort:** Medium

The `spotless/` module has a `build.gradle.kts` that compiles but `spotless/src/` contains
no Java source files. The integration does not exist.

### Steps

1. Create `spotless/src/main/java/io/princeofspace/spotless/PrinceOfSpaceStep.java`:
   - Implement `com.diffplug.spotless.FormatterStep` (or use `FormatterFunc`).
   - Accept config via constructor or static factory (language level, wrap style, etc.).
   - Instantiate `Formatter` and delegate `format(String rawUnix, File file)`.
   - Handle `Serializable` requirements for Spotless classloader isolation.
2. Create `spotless/src/test/java/io/princeofspace/spotless/PrinceOfSpaceStepTest.java`:
   - Use Spotless's `StepHarness` if available, or test directly.
   - Verify formatting output matches `core` for sample inputs.
   - Verify idempotency through the Spotless pipeline.
3. Add usage examples to `README.md` showing Gradle and Maven configuration.

### Key references

- `IMPLEMENTATION_PLAN.md` Phase 7
- Spotless FormatterStep API: `com.diffplug.spotless.FormatterStep` / `FormatterFunc`
- `spotless/build.gradle.kts` already has `compileOnly(libs.spotless.lib)`

### Verification

```
./gradlew :spotless:test
./gradlew build
```

---

## Task 5 — Implement the CLI tool

**Priority:** High — unlocks command-line usage; P2 goal
**Effort:** Medium

The `cli/` module has a `build.gradle.kts` and shadow JAR config but `cli/src/` contains
no Java source files. The CLI does not exist.

### Steps

1. Create `cli/src/main/java/io/princeofspace/cli/Main.java` using Picocli:
   ```
   prince-of-space [OPTIONS] [FILES...]
   Options:
     --check            Check formatting without modifying (exit 1 if unformatted)
     --java-version N   Java language level (default: 17)
     --stdin             Read from stdin, write to stdout
     -r, --recursive    Recursively find .java files in directories
     -v, --verbose      Verbose output
   ```
2. Implement file discovery: given directories, find `*.java` files. Respect `.gitignore`.
3. Implement `--check` mode: report which files would change, exit 0/1.
4. Implement parallel formatting using virtual threads (Java 21 source).
5. Create `cli/src/test/java/io/princeofspace/cli/MainTest.java`:
   - Test argument parsing.
   - Test `--check` exit codes.
   - Test `--stdin` round-trip.
6. Verify the shadow JAR runs: `java -jar cli/build/libs/prince-of-space-cli-*.jar --help`.

### Key references

- `IMPLEMENTATION_PLAN.md` Phase 8
- `cli/build.gradle.kts` (Main-Class already set to `io.princeofspace.cli.Main`)

### Verification

```
./gradlew :cli:test
./gradlew :cli:shadowJar
java -jar cli/build/libs/prince-of-space-cli-*.jar --check examples/outputs/java17/balanced-cont4-closingparen-true.java
```

---

## Task 6 — Add a `visit(LambdaExpr)` override

**Priority:** Medium — lambdas outside of method chains get default (non-width-aware) formatting
**Effort:** Small-medium

### Problem

There is no `visit(LambdaExpr)` override in `PrincePrettyPrinterVisitor`. Lambda formatting
only works correctly as a side effect of method call argument handling within chains. A
standalone block lambda (e.g. `Runnable r = () -> { ... }`) or a lambda in a non-chain
context gets JavaParser's default layout.

### Steps

1. Add failing tests:
   - Block lambda assigned to a variable: verify `{` on same line as `->`, body indented,
     `}` aligned with statement start.
   - Lambda as last argument of a non-chain call: verify opening `(` is never pushed to
     a new line (the google-java-format anti-pattern).
2. Override `visit(LambdaExpr, Void)` in `PrincePrettyPrinterVisitor`:
   - Short single-expression lambdas: keep inline.
   - Block lambdas: `{` on same line as `->`, body indented by `indentSize`, `}` aligned
     with the statement start.
3. Verify idempotency. Update goldens if affected.

### Key references

- `docs/02-formatting-decisions.md` § Lambda Expressions

### Verification

```
./gradlew :core:test
./gradlew build
```

---

## Task 7 — Width-aware `throws` clause wrapping

**Priority:** Medium — a long throws list currently exceeds all line limits
**Effort:** Small

### Problem

In `visit(MethodDeclaration)` and `visit(ConstructorDeclaration)`, the `throws` clause is
printed inline with no width check. A method throwing many exceptions will produce a line
that exceeds both `preferredLineLength` and `maxLineLength`.

### Steps

1. Add a failing test: method with 5+ thrown exception types that exceeds preferred length.
2. After printing `throws`, measure column + remaining types. If it exceeds preferred,
   wrap per `wrapStyle` (same logic as parameter lists / type lists).
3. Verify idempotency.

### Verification

```
./gradlew :core:test
./gradlew build
```

---

## Task 8 — Robustness: run against real-world codebases

**Priority:** Medium — validates all of the above against reality
**Effort:** Medium

### Steps

1. Create a test harness (can be a Gradle task or standalone script) that:
   - Clones well-known Java projects (e.g. Guava, Spring Boot, Apache Commons Lang).
   - Formats every `.java` file with default config.
   - Asserts no `FormatterException` (parse errors).
   - Asserts idempotency: `format(format(x)) == format(x)`.
   - Reports statistics: files processed, time taken, files that changed.
2. Fix any issues discovered. Common expected problem areas:
   - Comment preservation edge cases.
   - Deeply nested generics.
   - Annotation-heavy code.
   - Unusual but valid Java syntax.
3. Record results for future regression tracking.

### Key references

- `IMPLEMENTATION_PLAN.md` Phase 9

### Verification

The harness itself is the verification. Aim for zero parse errors and 100% idempotency
across all tested projects.

---

## Task 9 — Property-based idempotency testing

**Priority:** Medium — strengthens the determinism guarantee
**Effort:** Small-medium

### Steps

1. Add a test (e.g. `IdempotencyFuzzTest`) that generates random valid Java compilation
   units using JavaParser's AST construction API.
2. For each generated source, verify `format(format(x)) == format(x)`.
3. Run for a configurable number of iterations (e.g. 1000 in CI, more locally).
4. Optionally vary `FormatterConfig` parameters randomly across iterations.

### Verification

```
./gradlew :core:test
```

---

## Task 10 — Performance benchmarks

**Priority:** Low-medium — P0 goal states "near-instantaneous" but no measurement exists
**Effort:** Small

### Steps

1. Add a JMH benchmark module or a simple timed test that formats:
   - A single large file (~1000 lines).
   - A batch of 100+ files.
2. Measure wall-clock time and memory.
3. Compare against google-java-format on the same inputs.
4. Investigate the `est()` method in `PrincePrettyPrinterVisitor` which calls
   `e.toString()` for width estimation — this rebuilds subtree strings and may be a
   hot path for deeply nested expressions. Consider caching or a lighter measurement.

### Verification

Benchmark results documented; no regression test needed initially.

---

## Task 11 — Harden `BlankLineNormalizer` for nested types

**Priority:** Low
**Effort:** Small

### Problem

`BlankLineNormalizer.looksLikeTopLevelTypeDeclarationHeader()` requires the line to start
at column 0 (no leading whitespace). Nested class/interface/enum/record declarations are
indented, so blank lines after their opening `{` are incorrectly removed. The
`isTypeDeclarationBrace` check should match type declaration keywords regardless of
indentation level.

### Steps

1. Add a failing test: nested class with a blank line after its `{` — assert it is preserved.
2. Fix `looksLikeTopLevelTypeDeclarationHeader` to check `trimmedLine` patterns rather than
   requiring column-0 start, or rename/refactor to `looksLikeTypeDeclarationHeader`.
3. Verify no regressions in existing blank line tests.

### Verification

```
./gradlew :core:test
./gradlew build
```

---

## Task 12 — Documentation refresh

**Priority:** Low (but valuable)
**Effort:** Small

### Steps

1. Update `IMPLEMENTATION_PLAN.md`:
   - Mark Phase 4 as complete (showroom goldens aligned, golden tests in CI).
   - Update Phase 6 status (idempotency spot-checked; fuzz/benchmark pending).
   - Update Phase 7/8 status once Spotless/CLI are implemented.
2. Update `docs/02-formatting-decisions.md`:
   - Verify every stated behavior matches the implementation. Known discrepancies:
     - Try-with-resources `closingParenOnNewLine` (if fixed in Task 3b).
     - `trailingCommas` scope (if fixed in Task 3a).
3. Update `README.md` with usage examples once Spotless and CLI exist.

### Verification

Read-through; no automated check needed beyond `./gradlew build` (Spotless checks markdown).

---

## Reference: scenario map

For debugging and golden test work, this maps showcase scenarios to visitor code areas.

| Scenario | Construct | Primary visitor area |
|----------|-----------|---------------------|
| 1 | `implements` clause | `printImplementsClause`, `printTypeListGreedy` |
| 2 | Field annotations | Inherited annotation printing |
| 3 | Constructor parameters | `visit(ConstructorDeclaration)`, `printParametersList` |
| 4 | Method parameters + generics | `visit(MethodDeclaration)`, `printParametersList` |
| 5–6 | Method chains (streams, builders) | `visit(MethodCallExpr)`, chain helpers |
| 7 | Lambdas in chains | Chain helpers + `printArguments` + block lambda indent |
| 8 | Ternary expressions | `visit(ConditionalExpr)` |
| 9 | Binary `&&` / `\|\|` | `visit(BinaryExpr)`, `printBinaryGreedy` |
| 10 | If/else | `BraceEnforcer`, `visit(BlockStmt)` |
| 11 | Nested generics / collectors | `visit(MethodCallExpr)`, `printArguments` |
| 12 | Try-with-resources | `visit(TryStmt)` |
| 13 | Annotations (type-use) | `declarationAnnotations`, `inlineReturnTypeAnnotations` |
| 14 | Array initializers | `visit(ArrayInitializerExpr)` |
| 15 | Enum constants | `visit(EnumDeclaration)` |
| 16 | String concatenation | `visit(BinaryExpr)` (PLUS) |
| 17 | Nested interface | `visit(ClassOrInterfaceDeclaration)` |
| 18 | Complex generic method | `visit(MethodDeclaration)`, parameter wrapping |
| 19–20 | Small methods, interface defaults | Method / interface printing |
| 21+ | Records, sealed, switch, text blocks | `visit(RecordDeclaration)`, `visit(SwitchExpr)`, etc. |

## Reference: key files

| File | Role |
|------|------|
| `core/src/main/java/io/princeofspace/internal/PrincePrettyPrinterVisitor.java` | Primary formatting visitor |
| `core/src/main/java/io/princeofspace/internal/BlankLineNormalizer.java` | Blank line post-processing |
| `core/src/main/java/io/princeofspace/internal/FormattingEngine.java` | Parse → transform → print pipeline |
| `core/src/main/java/io/princeofspace/internal/BraceEnforcer.java` | Forced-brace AST transform |
| `core/src/main/java/io/princeofspace/model/FormatterConfig.java` | 8-knob config record + builder |
| `examples/inputs/<level>/FormatterShowcase.java` | Showroom inputs (java8, java17, java21) |
| `examples/outputs/<level>/<config>.java` | Golden outputs (36 files) |
| `core/src/test/java/io/princeofspace/FormatterShowcaseGoldenTest.java` | 36-way golden test harness |
| `core/src/test/java/io/princeofspace/WrappingFormattingTest.java` | Targeted wrapping regression tests |
| `core/src/test/java/io/princeofspace/FormatterTest.java` | General formatter behavior tests |
| `core/src/test/java/io/princeofspace/CommentPreservationTest.java` | Comment survival tests |

## Reference: verification checklist (every change)

1. `./gradlew :core:test` — all tests pass (includes golden tests after Task 1).
2. `./gradlew build` — full build green (Spotless, Checkstyle, SpotBugs, Error Prone).
3. Idempotency: `format(format(x)) == format(x)` for every new test case.
4. If goldens change: regenerate with `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens`, then verify all 3 Java levels stay consistent for shared source fragments.
