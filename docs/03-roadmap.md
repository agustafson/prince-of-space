# Roadmap — Prioritized Tasks

This document lists concrete, actionable tasks for bringing the formatter from its current
state to production readiness. Tasks are ordered by priority. An agent should work through
them sequentially (top to bottom), completing each before moving to the next unless a task
explicitly says it can be parallelised.

**Current state (April 2026):** 165 tests (129 unit/integration + 36 showroom golden) all
pass (2 optional tests skipped when env vars unset). `./gradlew build` is green (Spotless,
Checkstyle, SpotBugs, Error Prone, NullAway).
Phases 0–8 of `IMPLEMENTATION_PLAN.md` are complete. The core formatter handles the most
common Java constructs with width-aware wrapping across all 3 wrap styles and 8 config knobs.
Spotless integration (`PrinceOfSpaceStep`) and CLI tool (`Main` with Picocli, virtual-thread
batch formatting, `--check`/`--stdin`/`-r`/`-v`, git-aware file discovery) are implemented.
Golden tests run in the default `test` task.

---

## Task 1 — Enable golden tests in CI ✓ COMPLETE

Golden tests now run in the default `test` task (no `excludeTags`). All 165 tests pass (2 skipped).

---

## Task 2 — Enforce `maxLineLength` (hard limit) ✓ COMPLETE

`WrappingFormattingTest` asserts no line exceeds `maxLineLength` for method parameters,
call arguments, binary/string/implements/array cases; `PrincePrettyPrinterVisitor` uses
`wouldExceedMaxLine` / `maxLineLength` in greedy helpers (`printBinaryGreedy`,
`printGreedyCommaLines`, `printParametersList`, `printTypeListGreedy`, enum/array paths,
etc.) alongside `preferredLineLength`.

### Coverage note

The hard limit is enforced for the core wrapping primitives and for previously-missed constructs now
explicitly handled in `PrincePrettyPrinterVisitor`: multi-catch union types, long `assert` messages,
switch entry labels/guards, lambda parameter lists, and long generic type arguments in type-use positions.
Showcase scenarios include stress inputs for these paths.

---

## Task 3 — Implement missing documented formatting features ✓ COMPLETE

**3a** — `trailingCommas` for multi-line enums: `visit(EnumDeclaration)` + `FormatterTest`.

**3b** — Try-with-resources `closingParenOnNewLine`: `visit(TryStmt)` when multiple resources.

**3c** — Width-aware `printTypeParameters` in `PrincePrettyPrinterVisitor` + `FormatterTest`.

---

## Task 4 — Implement the Spotless integration ✓ COMPLETE

Implemented as `io.princeofspace.spotless.PrinceOfSpaceStep`. Uses `FormatterStep.create()`
with `FormatterFunc.needsFile()`. `FormatterConfig` is `Serializable` for Spotless classloader
isolation. Usage documented in `README.md`.

---

## Task 5 — Implement the CLI tool ✓ COMPLETE

Implemented as `io.princeofspace.cli.Main` (Picocli) with `--check`, `--stdin`,
`--java-version`, `-r`/`--recursive`, `-v`/`--verbose`, virtual-thread batch formatting,
and `git ls-files` integration. Shadow JAR: `./gradlew :cli:shadowJar`.

---

## Task 6 — Add a `visit(LambdaExpr)` override ✓ COMPLETE

`PrincePrettyPrinterVisitor.visit(LambdaExpr)` formats block and expression bodies (expression
bodies unwrap `ExpressionStmt` so no spurious `;`). `FormatterTest` covers standalone block
lambda, non-chain last-arg lambda, and expression lambdas in arguments.

---

## Task 7 — Width-aware `throws` clause wrapping ✓ COMPLETE

`printThrowsClause` + `WrappingFormattingTest.throwsClause_balanced_wrapsEachExceptionType`.

---

## Task 8 — Expand showcase inputs to cover all formatting-relevant constructs ✓ COMPLETE

Scenarios **31–43** are in all three `FormatterShowcase.java` inputs (long `for` / for-each /
`while`, standalone lambda, `Collections.sort` + lambda, constructor chaining, multi-catch,
nested ternary, long `assert`, `synchronized`, anonymous class, long `do-while`, long
`return`). Showroom goldens regenerated. Remaining optional coverage (e.g. multi-line
Javadoc in the 36-way matrix) can be added later; comment preservation stays in
`CommentPreservationTest`.

---

## Task 9 — Robustness: run against real-world codebases ✓ COMPLETE

`ExamplesCorpusFormatTest` (goldens + input parse), `OptionalRealWorldCheckoutFormatTest` (when
`PRINCE_REAL_WORLD_ROOT` is set). See `docs/robustness-harness.md`.

---

## Task 10 — Property-based idempotency testing ✓ COMPLETE

`IdempotencyFuzzTest`: pseudo-random configs over snippets (default 200 rounds,
`-Dio.princeofspace.fuzzIterations=N`) plus one AST-built `CompilationUnit` round-trip.

---

## Task 11 — Performance benchmarks ✓ COMPLETE (smoke + docs)

`FormatPerformanceSmokeTest` (large class + batch small formats). JMH / google-java-format
comparison deferred; see `docs/benchmarks.md`.

---

## Task 12 — Harden `BlankLineNormalizer` for nested types ✓ COMPLETE

`looksLikeTypeDeclarationHeader` uses trimmed lines; `BlankLineNormalizerTest.blankAfterNestedTypeDeclarationOpenBrace_preserved`.

---

## Task 13 — Documentation refresh ✓ COMPLETE

`IMPLEMENTATION_PLAN.md` Phases 4, 6, 9 updated; `docs/02-formatting-decisions.md` verified for
enum/array `trailingCommas` and try-with-resources `closingParenOnNewLine`; `README.md` CLI usage.

---

## Task 14 — Enhanced real-world evaluation harness

**Priority:** Medium — validates all formatting logic against production-scale Java code
**Detail:** See `docs/task-14-eval-harness.md` for the full specification.

### Summary

Replace `OptionalRealWorldCheckoutFormatTest` with a richer `RealWorldEvalTest` that:

- Targets **Guava** (`google/guava`) and **Spring Framework** (`spring-projects/spring-framework`)
- Runs **9 config permutations** per project (3 width bands × 3 wrap styles), with
  `preferred=80/max=100` as the primary stress config (forces wrapping on Guava's already
  100-char-limited code)
- Hard-asserts **zero parse errors** and **zero idempotency failures** across all runs
- Reports over-long non-comment lines as warnings (not failures — comments and string
  literals can legitimately exceed `maxLineLength`)
- Emits a structured Markdown report to `docs/eval-results/<date>.md`

Enabled via `PRINCE_EVAL_ROOTS` env var; excluded from default `test` task via `@Tag("eval")`.
Run with `./gradlew :core:evalTest`.

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
| 31 | Long `for` header | `visit(ForStmt)`, default / future header wrapping |
| 32 | Long `for-each` element type | `visit(ForeachStmt)` |
| 33 | Long `while` condition | `visit(WhileStmt)` |
| 34 | Standalone block lambda | `visit(LambdaExpr)`, `visit(BlockStmt)` |
| 35 | Lambda as last argument (non-chain) | `visit(LambdaExpr)`, `printArguments` |
| 36 | Constructor chaining `this(...)` | `visit(ExplicitConstructorInvocationStmt)` |
| 37 | Multi-catch | `visit(CatchClause)` |
| 38 | Nested ternary | `visit(ConditionalExpr)` |
| 39 | Long `assert` | `visit(AssertStmt)` |
| 40 | `synchronized` block | `visit(SynchronizedStmt)` |
| 41 | Anonymous class | `visit(ObjectCreationExpr)`, type body |
| 42 | Long `do-while` condition | `visit(DoStmt)` |
| 43 | Long `return` expression | `visit(ReturnStmt)`, expression wrapping |

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

1. `./gradlew :core:test` — all tests pass (includes golden tests after Task 1; optional real-world test may skip).
2. `./gradlew build` — full build green (Spotless, Checkstyle, SpotBugs, Error Prone).
3. Idempotency: `format(format(x)) == format(x)` for every new test case.
4. If goldens change: regenerate with `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens`, then verify all 3 Java levels stay consistent for shared source fragments.
