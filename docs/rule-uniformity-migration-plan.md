# Rule Uniformity Migration — Implementation Plan

> **Audience:** an autonomous coding agent (Sonnet-class is sufficient).
> **Goal:** make `WrapStyle` mean the same thing across every construct, fix the long tail of inconsistencies surfaced by the showroom, and align docs with code.
> **Format:** each task is a single PR. Do not combine tasks. Do not change scope inside a task.

---

## 0. Preflight (read this first, every task)

### 0.1 Repo conventions

- Source of truth for *why*: `docs/technical-decision-register.md` (TDR). Append-only.
- Source of truth for *what*: `docs/canonical-formatting-rules.md`. Update in lockstep with code.
- Implementation lives in `modules/core/src/main/java/io/princeofspace/internal/` and is **package-private** by default.
- `Formatter` (in `io.princeofspace`) must not import JavaParser or third-party libs directly.
- Tests for package-private classes live in `modules/core/src/test/java/io/princeofspace/internal/`.
- Showroom inputs are **only** `examples/inputs/<level>/FormatterShowcase.java`. Do not add other showroom inputs.

### 0.2 Build commands

```bash
./gradlew :core:test                                  # fast feedback (always run)
./gradlew build                                       # full gate (required before PR)
REGENERATE_SHOWROOM=true \
  ./gradlew :core:test --tests RegenerateShowroomGoldens   # refresh examples/outputs
```

### 0.3 TDD rule (per `.agents/skills/test-driven-development/SKILL.md`)

Each task lists "Tests to add". Add them **before** the implementation change, run them, see them fail, then make them pass. Do not add tests after the change.

### 0.4 Idempotency contract (do not break)

Every test that calls `Formatter.format` must also assert `format(format(x)).equals(format(x))`. Existing tests in `WrappingFormattingTest` already do this — copy that pattern.

### 0.5 Scope guardrails

For every task below, do **only** the items in "Modify". If you find an unrelated bug, write it down at the bottom of this file under "Discovered while working" and move on. Do not fix it in the same PR.

### 0.6 Commit / PR convention

- One commit per task unless the task explicitly says otherwise.
- PR title: `<area>: <imperative summary>` (e.g. `binaryexpr: unify BALANCED string concat with logical operators`).
- PR body must reference the task ID below (e.g. "Implements Task 2.1.").
- After a behavior change, `examples/outputs/**` will diff. Always regenerate goldens in the *same* PR with the regen command in 0.2.

---

## Task 1.1 — Add `WidthMeasurer` and migrate one caller ✅ COMPLETED

**Why:** `LayoutContext.est(Node)` uses `node.toString().length()` from JavaParser's default printer. That width is wrong for any node that itself wraps (lambdas, chains, string-concat, binary expressions). All later tasks rely on accurate width measurement.

### Read first

- `modules/core/src/main/java/io/princeofspace/internal/LayoutContext.java` — the `est(...)` method.
- `modules/core/src/main/java/io/princeofspace/internal/ArgumentListFormatter.java` — `argsFlatWidth` shows the existing lambda-header workaround.
- `modules/core/src/main/java/io/princeofspace/internal/MethodChainFormatter.java` — `chainFlatWidth` (similar pattern).

### Modify

- **Create** `modules/core/src/main/java/io/princeofspace/internal/WidthMeasurer.java` (package-private).
  - Single static method: `static int flatWidth(Node node, FormatterConfig fmt)`.
  - Implementation: walk the AST and sum widths recursively. Handle these cases explicitly:
    - `LambdaExpr` → header width + (block body? `"{ … }"`-collapsed width : expression width).
    - `MethodCallExpr` chain → sum receiver + `.name(args)` segments.
    - `BinaryExpr` → `left + " op " + right` (recursive).
    - `StringLiteralExpr` / `TextBlockLiteralExpr` → length of the source-form literal (not the un-escaped value).
    - Default → fall back to `node.toString().length()` (parity with today, but only for leaves).
  - Do **not** call into the pretty-printer.
- **Migrate one caller only:** replace `ctx.est(node)` with `WidthMeasurer.flatWidth(node, ctx.fmt())` in `ArgumentListFormatter.argsFlatWidth`. Leave every other `ctx.est` site alone — those move in Task 1.2.

### Tests to add (failing first)

- `modules/core/src/test/java/io/princeofspace/internal/WidthMeasurerTest.java`
  - `flatWidth_simpleIdentifier_matchesLength`
  - `flatWidth_methodCallChain_sumsAllSegments`
  - `flatWidth_blockLambda_treatsBodyAsBraced`
  - `flatWidth_stringLiteral_includesQuotes`
  - `flatWidth_binaryExpr_includesOperatorSpacing`

Each test parses a snippet with JavaParser, picks one node, and asserts `WidthMeasurer.flatWidth` against the hand-counted width.

### Acceptance

```bash
./gradlew :core:test
```

All tests pass. **No** showroom golden changes (this task is a behavior-preserving refactor of one site).

### Don't

- Don't replace every `ctx.est(...)` call now.
- Don't expose `WidthMeasurer` outside `io.princeofspace.internal`.
- Don't modify the pretty-printer logic.

---

## Task 1.2 — Migrate remaining `ctx.est` callers and delete `est` ✅ COMPLETED

### Read first

- All call sites of `LayoutContext.est`. Find them with:

```bash
rg -n "\bctx\.est\(" modules/core/src/main/java
rg -n "\.est\(" modules/core/src/main/java/io/princeofspace/internal/LayoutContext.java
```

### Modify

- Replace every remaining `ctx.est(node)` with `WidthMeasurer.flatWidth(node, ctx.fmt())`.
- Remove the `est` method from `LayoutContext`.
- If a caller relied on a specific `est` quirk that `WidthMeasurer` does not reproduce, write a new test that pins the desired behavior and add the case to `WidthMeasurer`.

### Tests to add

- `WidthMeasurerTest.flatWidth_lambdaWithComplexGenerics_matchesLambdaHeaderHelper`
- `WidthMeasurerTest.flatWidth_chainedCallWithLambdaArg_doesNotInflate`

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
git diff examples/outputs
```

A small set of golden lines may shift (where the old `est` was incorrect). Inspect the diff visually; every change should be either (a) a previously over-long line now wrapping, or (b) a previously over-eager wrap now staying inline. If you see a change that makes a line *worse*, stop and add a regression test.

### Don't

- Don't modify the printer logic. This is purely the measurement.

---

## Task 2.1 — Unify `BALANCED` for string concatenation ✅ COMPLETED

**Why:** `BALANCED` everywhere else means "fit-or-tall". For `+` it currently means "greedy". Make it "fit-or-tall" too.

### Read first

- `modules/core/src/main/java/io/princeofspace/internal/BinaryExprFormatter.java` — focus on `useGreedyPackingForList` and `format`.
- `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java` — `stringConcatenation_balanced_keepsGreedyFirstLinePacking` (this test pins the *current* wrong behavior and must be updated, see below).

### Modify

- In `BinaryExprFormatter.useGreedyPackingForList`, simplify to:

```java
private boolean useGreedyPackingForList(BinaryExpr.Operator op) {
    return ctx.fmt().wrapStyle() == WrapStyle.WIDE;
}
```

- In `WrappingFormattingTest`, rename `stringConcatenation_balanced_keepsGreedyFirstLinePacking` to `stringConcatenation_balanced_putsEachOperandOnItsOwnLine` and update assertions to match the `_narrow_` variant just below it. Same for `stringConcatenation_balanced_java21_keepsGreedyFirstLinePacking`.
- Update `docs/formatting-rules.md` "Binary Operator Wrapping" to drop the `+`-special-case prose.
- Append TDR entry **TDR-008: WrapStyle is uniform across constructs** to `docs/technical-decision-register.md`. Use the existing entry style. Reference this task ID and `docs/rule-uniformity-migration-plan.md`.

### Tests to add (failing first)

- `WrappingFormattingTest.stringConcatenation_wide_keepsGreedyPacking` — pin the *unchanged* WIDE behavior so we don't regress it.
- `WrappingFormattingTest.stringConcatenation_balanced_putsEachOperandOnItsOwnLine` — new authoritative BALANCED test.

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
```

`BALANCED` golden files (cont4 and cont8, both `closingparen` values, all four language levels — i.e. 16 files) will diff at every `+`-concat site. Inspect at least one (e.g. `examples/outputs/java17/balanced-cont4-closingparen-true.java` scenario 16) and confirm each operand is on its own line.

### Don't

- Don't touch enum constants, array initializers, or type parameters in this PR. They are Tasks 2.2 / 2.3 / 2.4.
- Don't touch the WIDE behavior.

---

## Task 2.2 — Make enum constant lists obey `WrapStyle` ✅ COMPLETED

### Read first

- `modules/core/src/main/java/io/princeofspace/internal/DeclarationFormatter.java` — find the enum-constant printing code (search for `EnumDeclaration` and `EnumConstantDeclaration`).
- `modules/core/src/main/java/io/princeofspace/internal/ArgumentListFormatter.java` — `printGreedyCommaLines` and the BALANCED/NARROW one-per-line branch are reusable.

### Modify

- Extract the comma-separated-list printer in `ArgumentListFormatter` so it can be called from `DeclarationFormatter` for enum constants.
- Replace the enum-constant printer with a call into that shared helper, passing the same `WrapStyle` rules used by argument lists.

### Tests to add

- `modules/core/src/test/java/io/princeofspace/internal/EnumConstantWrappingTest.java`
  - `enumConstants_balanced_oneConstantPerLineWhenOverflow`
  - `enumConstants_wide_greedyPacking`
  - `enumConstants_narrow_oneConstantPerLine`

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
```

`WrappingFormattingTest.enumConstants_wide_*` tests must still pass unchanged. BALANCED enum scenarios in goldens will switch to one-per-line.

### Don't

- Don't change the *position* of `;` or comment handling around enum constants. Use the existing helpers.

---

## Task 2.3 — Make array initializers obey `WrapStyle` ✅ COMPLETED

Same shape as Task 2.2.

### Read first

- `PrincePrettyPrinterVisitor.visit(ArrayInitializerExpr ...)`.
- `WrappingFormattingTest.arrayInitializer_wide_keepsRoomForClosingBrace` and `maxLineLength_enforcedForWideArrayInitializer`.

### Modify

- Route array-initializer wrapping through the same comma-list helper used in Task 2.2.

### Tests to add

- `WrappingFormattingTest.arrayInitializer_balanced_putsEachElementOnItsOwnLine`
- `WrappingFormattingTest.arrayInitializer_narrow_putsEachElementOnItsOwnLine`

### Acceptance

Same as Task 2.2. Existing WIDE assertions must keep passing.

---

## Task 2.4 — Make type-parameter lists wrap ✅ COMPLETED

### Read first

- `DeclarationFormatter.printTypeParameters` (or equivalent — search for `TypeParameter`).
- `WrappingFormattingTest.methodParameters_wide_packByPhysicalWidthAfterFirstForcedWrap` — type-parameter overflow appears here.

### Modify

- When the type-parameter list's flat width pushes the declaration past `lineLength`, wrap the parameter list using the shared comma-list helper. Type parameters always wrap the **whole list** when they wrap (don't split the angle brackets across multiple wraps).

### Tests to add

- `WrappingFormattingTest.typeParameters_balanced_wrapsEachWhenOverflow`
- `WrappingFormattingTest.typeParameters_wide_packsGreedily`
- `WrappingFormattingTest.typeParameters_narrow_oneParameterPerLine`

### Acceptance

Same as Task 2.2. Existing goldens for very long generic method declarations will change shape; the change should make scenario 18 lines no longer overflow `lineLength=120`.

### Don't

- Don't change how the angle-bracket close-character is positioned. It must mirror the open-bracket column in the wrapped form, like `)` does in `closingParenOnNewLine=true`.

---

## Task 3.1 — Wrap path for `extends` clause ✅ COMPLETED

### Read first

- `DeclarationFormatter` — find both the `extendedTypes` and `implementedTypes` blocks.
- `TypeClauseFormatter` — `printTypeClauseListWithWrap` (already used for `implements`/`permits`/`throws`).

### Modify

- Replace the `extends` print with `typeClauseFormatter.printTypeClauseListWithWrap("extends", n.getExtendedTypes(), arg, ...)`. Match argument shape used for `implements`.
- Add a showroom scenario: in each `examples/inputs/javaXX/FormatterShowcase.java`, append a small interface declaration that has 6+ super-interfaces so it overflows `lineLength=120`. Pick the next free scenario number (see `docs/showroom-scenarios.md`).

### Tests to add

- `WrappingFormattingTest.extendsClause_balanced_wrapsEachInterface` (mirror `implementsClause_balanced_wrapsEachType`).
- Scenario doc update: `docs/showroom-scenarios.md` — add the new scenario number under the appropriate language-level row.

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
```

### Don't

- Don't add new showroom *files*. Append to the existing `FormatterShowcase.java` per scenario.

---

## Task 3.2 — Drop the `column - 2*indentSize` chain shift inside binary expressions ✅ COMPLETED

### Read first

- `BinaryExprFormatter` — search for `printExprWithTrailingCommentAfterWithNestedChainIndent` and any callers using `column - 2 * indentSize`.

### Modify

- Replace the manual column adjustment with the same indent path that top-level chains use. The chain dots should land at `currentIndent + continuationIndentSize`, the same as everywhere else.
- Remove any helper introduced solely for this case.

### Tests to add

- `WrappingFormattingTest.logicalAnd_withWrappedMethodChainOperand_chainDotsAtContinuationIndent` — assert chain leading-dot column equals `&& items` column + `continuationIndentSize`. Remove or replace the existing `…_indentsChainDeeperThanBooleanOperator` test if its expectation was the old shift.

### Acceptance

Same. Showroom scenario 13b (or your equivalent nested-chain inside `&&`) will reflow.

### Don't

- Don't make this PR also touch top-level chain rules. Top-level chain logic is already correct.

---

## Task 3.3 — Document continuation indent as additive ✅ COMPLETED

Pure docs.

### Modify

- `docs/formatting-rules.md` — locate the `continuationIndentSize` section, replace any wording that implies "absolute from statement start" with explicit "added on top of the enclosing block indent". Show one before/after example with `indentSize=4, continuationIndentSize=4` so the visual depth is clear.
- Append TDR entry **TDR-009: continuationIndentSize is additive**.

### Tests to add

None.

### Acceptance

```bash
./gradlew build       # picks up docs lints if any are wired up
```

---

## Task 4.1 — Single rule for `closingParenOnNewLine` ✅ COMPLETED

### Read first

- `modules/core/src/main/java/io/princeofspace/internal/CommentUtils.java` — `shouldGlueWrappedClosingParen`.
- All callers:

```bash
rg -n "shouldGlueWrappedClosingParen" modules/core/src/main/java
```

### Modify

- Delete `shouldGlueWrappedClosingParen` and every caller's reference to it.
- The new rule: when `closingParenOnNewLine=true` *and* the list wrapped, emit a newline before `)` and place `)` at the call's indent column. No exceptions — single arg, block lambda, lambda-in-lambda all behave the same.

### Tests to add

- `WrappingFormattingTest.closingParen_singleBlockLambdaArg_onItsOwnLineWhenEnabled`
- `WrappingFormattingTest.closingParen_singleNestedCall_onItsOwnLineWhenEnabled`

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
```

This will produce the **largest** golden diff of any task. Spot-check at least:

- `examples/outputs/java17/wide-cont4-closingparen-true.java` scenario 51 (nested lambda call).
- `examples/outputs/java17/balanced-cont4-closingparen-true.java` scenario 39b (exception message).

In both, the close `)` should now sit at the call's indent column with no exceptions.

### Don't

- Don't introduce a new config option to preserve the old glue behavior. We are deliberately removing it.

---

## Task 5.1 — Lambda parameter list mis-indent (showcase scenario 44) ✅ COMPLETED

### Read first

- `examples/outputs/java17/balanced-cont4-closingparen-true.java` scenario 44 — read the rendered output to see the bug shape.
- `MethodChainFormatter` — argument printing for lambda-args.
- `LambdaExpr` printing in `PrincePrettyPrinterVisitor`.

### Modify

- When a lambda parameter list itself wraps inside a wrapped chain call, parameters indent at `currentIndent + continuationIndentSize` (one level deeper than the open `(`), and the close `)` sits at the same column as the open `(`. No deeper indent.

### Tests to add (failing first)

- `WrappingFormattingTest.lambdaParameterList_insideWrappedChainCall_alignsParametersAndCloseParen` — assert (a) all parameter lines have the same leading-space count, (b) the close `)` line has leading spaces equal to the open `(` line.

### Acceptance

Same. Scenario 44 in goldens should show clean alignment.

---

## Task 5.2 — `for(init; cond; update)` and for-each headers wrap ✅ COMPLETED

### Read first

- `PrincePrettyPrinterVisitor.visit(ForStmt ...)` and `visit(ForEachStmt ...)`.
- Showcase scenarios 31 (classic for) and 32 (for-each).

### Modify

- Treat the classic-for header as a 3-element semicolon-separated list under the same `WrapStyle` rules used for arguments: when the header overflows, place each clause on its own continuation line.
- For-each: when the header overflows, break after the `:` to a continuation line.

### Tests to add

- `WrappingFormattingTest.forLoopHeader_balanced_wrapsAllThreeClauses`
- `WrappingFormattingTest.forEachLoopHeader_balanced_wrapsAfterColon`

### Acceptance

Same. Goldens for scenarios 31 and 32 reflow.

---

## Task 5.3 — `switch` case-label wrap (showcase scenario 45)

### Read first

- `PrincePrettyPrinterVisitor.visit(SwitchEntry ...)` (or equivalent).
- Showcase scenario 45 — long case label.

### Modify

- Treat case labels as a comma-separated list governed by `WrapStyle` rules, including the `when` guard text in the flat-width measurement.

### Tests to add

- `WrappingFormattingTest.switchCaseLabel_balanced_wrapsEachLabelWhenOverflow`

### Acceptance

Same. Scenario 45 (Java 17+ trees only) reflows.

---

## Task 5.4 — Trailing space after `assert … :`

### Read first

- `PrincePrettyPrinterVisitor.visit(AssertStmt ...)`.

### Modify

- Strip the trailing space when there is no message; ensure exactly one space after `:` when there is.

### Tests to add

- `modules/core/src/test/java/io/princeofspace/internal/AssertStmtFormattingTest.java`
  - `assertWithoutMessage_hasNoTrailingSpace`
  - `assertWithMessage_hasSingleSpaceAfterColon`

### Acceptance

`./gradlew :core:test`. No golden churn expected outside the affected line.

---

## Task 5.5 — `try-with-resources` uses continuation indent

### Read first

- `PrincePrettyPrinterVisitor.visit(TryStmt ...)` — find the resource-list printing block.

### Modify

- Replace the column-alignment branch with the standard comma-list helper (semi-colon separator). Each wrapped resource indents at `currentIndent + continuationIndentSize`. Close `)` follows the `closingParenOnNewLine` rule.

### Tests to add

- `WrappingFormattingTest.tryWithResources_balanced_indentsByContinuationIndentNotKeyword`

### Acceptance

Same. Scenario for try-with-resources in goldens reflows.

---

## Task 6.1 — Implement `AnnotationArranger`

### Read first

- `modules/core/src/main/java/io/princeofspace/internal/AnnotationArranger.java` (currently a placeholder).
- `docs/formatting-rules.md` annotation section for the rules to enforce.

### Modify

- Walk all `NodeWithAnnotations` nodes. For declaration annotations (on classes, methods, fields, parameters that *are* declarations), emit each on its own line. For type-use annotations (`@NonNull String`), keep inline.
- Use JavaParser's `target` metadata where available; otherwise fall back to: an annotation directly preceding a declaration name is a declaration annotation, otherwise type-use.

### Tests to add

- `modules/core/src/test/java/io/princeofspace/internal/AnnotationArrangerTest.java`
  - `declarationAnnotations_eachOnOwnLine`
  - `typeUseAnnotations_stayInline`
  - `mixed_declAndTypeUse_keepRespectiveStyles`

### Acceptance

```bash
./gradlew :core:test
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
```

Expect minor golden diffs anywhere annotations were already on their own line "by accident" of input shape — those should now be enforced rather than relied on.

---

## Task 6.2 — `BlankLineNormalizer` carve-out removal

### Read first

- `BlankLineNormalizer.collapseAfterOpeningBrace` (or equivalent — search for the type-declaration carve-out).

### Modify

- Drop the carve-out so no construct emits a blank line directly after `{`. The "blank between members" rule still produces one blank line between two declarations inside the type, which is the correct visual.
- Update `docs/formatting-rules.md` blank-line section to remove the carve-out.

### Tests to add

- `modules/core/src/test/java/io/princeofspace/internal/BlankLineNormalizerTest.java` — extend with:
  - `interface_noBlankLineAfterOpeningBrace`
  - `class_noBlankLineAfterOpeningBrace`
  - `enum_noBlankLineAfterOpeningBrace`

### Acceptance

Same. Goldens for type bodies will lose the blank line after `{`.

---

## Task 6.3 — Final docs + `RuleUniformityTest`

### Read first

- All goldens in `examples/outputs/**` post Tasks 2.1–6.2.
- `docs/formatting-rules.md`.

### Modify

- `docs/formatting-rules.md`: remove every per-construct asterisk for `+`, type parameters, enum constants, array initializers, `extends`, try-with-resources. Replace with a single "WrapStyle behavior" section describing the three uniform shapes.
- `docs/technical-decision-register.md`: append **TDR-010: Showroom rule uniformity migration complete** referencing this plan.
- New test class `modules/core/src/test/java/io/princeofspace/RuleUniformityTest.java` with one parameterised test per construct family × `WrapStyle`. The test asserts the macro-shape (greedy / fit-or-tall / always-wrap) per Rule 3 of the analysis.

### Acceptance

```bash
./gradlew build
```

All tests pass. `git diff examples/outputs` empty (since previous tasks already regenerated goldens).

---

## Discovered while working

> Append items here when you spot an unrelated issue mid-task. Each entry: `<task-id> · <one-line summary> · <file:line>`. Do **not** fix in the same PR.

- _(empty)_
