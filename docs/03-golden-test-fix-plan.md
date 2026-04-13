# Golden Test Fix Plan

This document describes the showroom golden matrix (`examples/inputs/` → `examples/outputs/`), how to run it, and **open issues** for agents to work through in a sensible order. Keep it updated as issues are resolved.

## Status

- **Showroom goldens:** `./gradlew :core:showroomGoldenTest` should pass **36/36** (verify after substantive formatter changes).
- **Default `test` task:** Still **excludes** tag `showroom-golden` (see `core/build.gradle.kts`). Run showroom tests explicitly when working on formatting parity.
- **CI:** Consider removing the exclude so showroom tests run in CI once the open issues below are addressed and the team agrees goldens are stable.

## Test matrix

**3 Java levels** × **12 config combos** = **36 tests**

| Axis | Values |
|------|--------|
| Java level | `java8`, `java17`, `java21` |
| Wrap style | `wide`, `balanced`, `narrow` |
| Continuation indent | `cont4`, `cont8` |
| Closing paren | `true`, `false` |

Each test formats `examples/inputs/<level>/FormatterShowcase.java` with the parsed config (from the golden filename) and compares the result byte-for-byte to `examples/outputs/<level>/<wrap>-cont<N>-closingparen-<bool>.java`.

**Harness:** `core/src/test/java/io/princeofspace/FormatterShowcaseGoldenTest.java` (tag `showroom-golden`).

### Inputs and scenario coverage

- **`java8`:** Scenarios 1–20 (baseline constructs).
- **`java17`:** Same core as java8, plus modern syntax where valid for 17 (e.g. `var`, text blocks, records, sealed types). Scenarios run through **25** in the showcase input.
- **`java21`:** Adds scenarios **26–30** (e.g. record patterns, guarded `switch`, virtual threads, structured concurrency, sequenced collections). For shared scenarios **1–25**, output should match **java17** for the same golden filename wherever the source text matches; if it diverges, suspect a bug or a stale golden.

**Cross-level rule:** For the same source fragment, formatting rules are intended to be **identical** across levels—only `LanguageLevel` (parser) changes.

## Golden files: when they are authoritative

- **During parity work:** The formatter is changed until it matches the committed goldens (TDD from goldens).
- **After a deliberate formatter improvement:** If the team agrees the **new** behavior is correct (e.g. greedy packing fixed), **update all affected goldens** (often all 36, or all files for a given wrap style) and add or adjust regression tests in `core/src/test/java/io/princeofspace/` (e.g. `WrappingFormattingTest`, `FormatterTest`).
- **Do not** change goldens only to silence a test when the new output is worse or inconsistent with documented behavior—fix the formatter or the doc instead.

## Resolved / current (2026)

- **Issues 1–2 (packing):** Greedy comma/parameter/binary packing now uses the real `SourcePrinter` column after continuation indent (removed synthetic `wideParameterOffset` / `wideTypeListOffset` / `columnWithOffset` heuristics). Wrapped parameter and argument lists adjust the last line’s budget using `closingParenOnNewLine` (extra width when `)` is on its own line; reserve for `) {` when it is not). See `PrincePrettyPrinterVisitor#printParametersList`, `#printGreedyCommaLines`, `#printBinaryGreedy`, `#printTypeListGreedy`, enum greedy constants.
- **Issue 5 (goldens):** Showroom outputs were regenerated for java8/java17/java21; use `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens` when the formatter changes intentionally.
- **Issues 3–4 (docs):** `IMPLEMENTATION_PLAN.md` and `docs/02-formatting-decisions.md` updated for type-clause `{` + `closingParenOnNewLine`. NARROW `&&`/`||` use one operand per line (same as BALANCED for logical chains).
- **Wrapped method chains:** Multi-segment chains use a leading dot per line (receiver alone on the first line); a single `.method()` after a simple receiver stays on one line (`items.stream()`). See `docs/02-formatting-decisions.md` § Method chaining.

## Open issues (work order for agents)

Historical reference — most items below are **done**; keep the file for scenario maps and debugging notes.

### Issue 1 — WIDE `cont4` packs too conservatively vs WIDE `cont8` (high value)

**Severity:** Medium — affects perceived quality and consistency of WIDE layout.

**Observation:** `wide-cont4` and `wide-cont8` share the same `preferredLineLength` (typically 120), but **`cont4` breaks earlier** than `cont8` on the same constructs. Examples (java8 goldens are the clearest reference):

- **Scenario 3 (constructor):** `wide-cont4` splits `boolean validateOnConstruction, String defaultLocale,` and `ExecutorService executorService` across lines even though a single packed line fits under the limit; `wide-cont8` often keeps the trailing parameters on one line.
- **Scenario 15 (enum constants):** break positions differ between cont4 and cont8 for the same enum block.
- **Scenario 18 (`transformAndCollect`):** similar extra breaks under cont4.

**Likely cause:** Continuation-indent width is applied as a **budget reduction** (`wideParameterOffset` or similar) even when the semantic line budget should be the same; or column measurement in `printGreedyCommaLines()` / parameter packing does not match `SourcePrinter` cursor.

**Work:**

1. Inspect `PrincePrettyPrinterVisitor` greedy packing and offsets for WIDE parameter/argument lists and comma-separated lines.
2. Align behavior so **cont4 and cont8** only differ by **indent of continuation lines**, not by **where** breaks occur when both fit the same character limit.
3. Use vendored `SourcePrinter` cursor column as ground truth where applicable (`com/github/javaparser/printer/SourcePrinter.java`).

**Verify:** `./gradlew :core:test` and `./gradlew :core:showroomGoldenTest`. Expect to **update all 36 goldens** (or at least every `wide-cont4-*` file) once the formatter matches the improved packing.

---

### Issue 2 — `closingParenOnNewLine=true` should not shrink the last packed line budget

**Severity:** Low–medium — subtle UX inconsistency.

**Observation:** When `closingParenOnNewLine` is true, the closing `)` is printed on its own line. The **last line of parameters** then does not need to reserve space for `) {`, yet packing often matches the `false` variant’s breaks (e.g. constructor Scenario 3 in `wide-cont4-closingparen-true` vs `false`).

**Work:** When computing whether the next comma-separated chunk fits on the current line, if `closingParenOnNewLine` is true, **do not** count the closing `)` (and following `{` if applicable) against the last content line’s budget.

**Verify:** Golden diffs for `*-closingparen-true` vs `false` should show **only** delimiter placement (and indentation of `)`), not different parameter line breaks, unless a line is near the real limit.

**Depends on:** Issue 1 in practice (same packing code paths).

---

### Issue 3 — Docs vs goldens: type clauses and `closingParenOnNewLine`

**Severity:** Low (documentation).

**Observation:** `IMPLEMENTATION_PLAN.md` states that type clauses (`implements` / `permits` / `extends`) **never** reference `closingParenOnNewLine` (see the “Type clauses” bullet under formatting decisions). The **goldens** show the opening `{` after a **wrapped** type clause following the same brace rule as parameter lists (e.g. `AutoCloseable {` vs `AutoCloseable` + newline + `{` when `closingParenOnNewLine` is true).

**Work:** Update `IMPLEMENTATION_PLAN.md` and `docs/02-formatting-decisions.md` to describe the **actual** rule: e.g. when a type clause wraps, the `{` may follow the same “closing delimiter on new line” style as `)` for parameter lists. Do not change goldens solely to satisfy the old sentence—**fix the docs** to match shipped behavior unless the product decision is to change behavior.

---

### Issue 4 — Docs vs goldens: NARROW binary operators

**Severity:** Low (documentation).

**Observation:** Docs sometimes describe NARROW as strictly **one element per line**. The goldens for binary `&&` / `||` (Scenario 9) use **paired** operands per line for readability.

**Work:** Update docs to describe NARROW for logical operators as **paired (or opportunistic) packing**, not strict one-token-per-line, **or** decide to change the formatter and then update all NARROW goldens—prefer doc alignment unless there is a product reason to enforce one-per-line.

---

### Issue 5 — Regenerated java17/java21 goldens may embed formatter bugs

**Severity:** Medium (process).

**Observation:** If `java17` / `java21` `wide` / `narrow` outputs were normalized from formatter output at a point in time, they **inherit** any greedy-packing bugs (Issue 1). Fixing Issue 1 will require **regenerating or hand-fixing** those files again for consistency with `java8`.

**Work:** After Issue 1 (and 2) are fixed, regenerate or systematically update **all** showroom goldens so **java8**, **java17**, and **java21** agree on layout for identical source spans.

---

### Issue 6 — Historical failure categories (still useful for debugging)

The following were used when diffing formatter vs goldens; they remain relevant regression targets:

| Area | Scenarios | Notes |
|------|-----------|--------|
| Greedy comma/binary packing | 1, 3, 4, 9, 14–16, 18 | Overlap with Issue 1 |
| Lambda body indent in chains | 7 | Double continuation vs statement indent |
| Closing `)` after block lambdas / nested calls | 7, 11 | `})` vs split lines |
| String concat + WIDE | 16 | Break positions vs greedy packing |
| Nested `Collectors.groupingBy` | 11 | Argument wrapping with chains |

Add **focused unit tests** in `WrappingFormattingTest` / `FormatterTest` when fixing each area so showroom stays a **slow** integration check, not the only signal.

---

## Verification checklist (every change)

1. `./gradlew :core:test`
2. `./gradlew :core:showroomGoldenTest`
3. Idempotency on representative inputs: `format(format(x))` equals `format(x)` (see existing tests or add one).
4. If goldens change: ensure **all three levels** stay aligned for identical source (Issue 5).

## Key files

| File | Role |
|------|------|
| `core/src/main/java/io/princeofspace/internal/PrincePrettyPrinterVisitor.java` | Primary formatting implementation |
| `core/src/main/java/io/princeofspace/internal/BlankLineNormalizer.java` | Blank line post-processing |
| `com/github/javaparser/printer/SourcePrinter.java` | Vendored printer / cursor |
| `examples/inputs/<level>/FormatterShowcase.java` | Showroom inputs |
| `examples/outputs/<level>/<config>.java` | Golden outputs |
| `core/src/test/java/io/princeofspace/FormatterShowcaseGoldenTest.java` | 36-way golden test |
| `core/src/test/java/io/princeofspace/WrappingFormattingTest.java` | Targeted wrapping regressions |
| `core/src/test/java/io/princeofspace/FormatterTest.java` | General formatter behavior |

## Scenario → visitor map (quick reference)

| Scenario | Construct | Primary area in visitor |
|----------|-----------|---------------------------|
| 1 | `implements` | Type declaration / greedy comma lines |
| 2 | Field annotations | Annotations |
| 3 | Constructor parameters | `printParametersList` |
| 4 | Method parameters + generics | `printParametersList` |
| 5–6 | Chains | `MethodCallExpr`, chain wrapping |
| 7 | Lambdas in chains | Chains + arguments + lambda indent |
| 8 | Ternary | `ConditionalExpr` |
| 9 | Binary `&&` / `\|\|` | `BinaryExpr`, `printBinaryGreedy` |
| 10 | If/else | Braces / blocks |
| 11 | Nested calls / collectors | `MethodCallExpr`, arguments |
| 12 | Try-with-resources | `TryStmt` |
| 13 | Annotations (incl. type-use) | Annotations / method signature |
| 14 | Array initializers | Array init |
| 15 | Enums | `EnumDeclaration`, greedy lines |
| 16 | String concatenation | `BinaryExpr` (`+`) |
| 17 | Nested interface | Type declaration |
| 18 | Complex generics | Parameters / type params |
| 19–20 | Small methods / interface defaults | Method / interface printing |
| 21+ | Records, sealed, switch, text blocks, Java 21 features | As per language level |

## Debugging tips

- Point `DebugTest` (if present) at a single config: `WrapStyle`, `continuationIndentSize`, `closingParenOnNewLine`, `LanguageLevel`, then diff against the matching golden.
- Prefer **small regression tests** that pin one behavior, then run the full showroom suite.
