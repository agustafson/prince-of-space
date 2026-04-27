# Implementation plan: stacked `)` and continuation-indent in nested wrapping

> **Status:** Proposal / draft. Working doc, not a roadmap entry. Once accepted,
> the canonical contract is updated in `docs/canonical-formatting-rules.md`
> (Rule 8) and motivation captured in a new TDR. This file can then be deleted
> or moved into the eval-results history.

## Problem

When a wrapped construct is **nested inside another wrapped construct on the
same physical line**, the formatter produces two related defects:

1. **Stacked `)` closers** — multiple `)` lines aligned vertically at the same
   column, none of which sits under its matching `(`.
2. **Misaligned continuation indent** — wrapped argument lists that open
   mid-line (e.g. on a ternary continuation) print their items at the same
   column as the surrounding continuation operator, with the closing `)` even
   further left.

Both defects share one underlying cause: layout decisions assume the opener
`(` sits on a fresh line at the printer's natural indent column. That
assumption breaks the moment a nested wrapped delimiter pair is opened
mid-line.

The user has reported variations of this bug repeatedly. Earlier patches
(TDR-017) addressed the *args-indent* aspect for nested calls but stopped
short of fixing closer placement and the ternary-/binary-continuation case.

## Failing repros (current behavior)

A repro probe has been committed in
`modules/core/src/test/java/io/princeofspace/StackedClosingParensReproTest.java`.
It is a non-asserting probe (prints output to stdout) so the build stays
green; it should be promoted to an asserting test once the fix is in.

Config used: `wrapStyle=balanced`, `closingParenOnNewLine=true`,
`lineLength=80`, `indentSize=4`.

### Case 0 — `methodA(methodB(a, b, c, d, e, f, g));`

```java
class C {
    void m() {
        methodA(methodB(
                aaaaaaa,
                bbbbbbb,
                ccccccc,
                ddddddd,
                eeeeeee,
                fffffff,
                ggggggg
        )      // ← methodB's ) at col 8 (its `(` is at col 22)
        );     // ← methodA's ) at col 8 (its `(` is at col 14)
    }
}
```

### Case 2 — triple nest

```java
        outerOuter(outer(methodB(
                aaaaaaa, …, ggggggg
        )
        )
        );     // ← three `)` stacked at col 8
```

### Case 6 — ternary inside a wrapped call (user's "ternary" report)

```java
return methodA(longConditionExpressionForCheck
        ? methodB(
        longArg1,             // ← args at col 16, same column as `?`/`:`
        longArg2, …
        longArg6
)                              // ← methodB's ) at col 8, LEFT of the `?`
        : methodC(
        longArg1, …
)                              // ← methodC's ) at col 8
);                             // ← methodA's ) at col 8
```

The ternary expression formatter emits `?`/`:` continuation lines at column
16, but `methodB` then triggers its own wrapped-list scope which pushes the
**absolute** printer indent to col 16 — so the args land at col 16 (under
`?`), and the `)` closers pop back to col 8 (left of `?`, two levels below
where they belong).

All four reported scenarios reproduce. All are idempotent — the bug is stable
output, not oscillation.

## Root cause

Two coupled assumptions in `PrincePrettyPrinterVisitor.printArguments` and
`DeclarationFormatter.format{Constructor,Method,Record}`:

1. **Closer placement assumes opener at line start.** The own-line closer
   (`closingParenOnNewLine=true`) is emitted as `println(); print(")")` after
   popping any wrapped-list scope. The result lands at the *current printer
   indent*, which equals the line-indent column **only when the opener was on
   a fresh line at that same indent**. For nested expression openers, the
   opener was mid-line and its closer ends up at an arbitrary column unrelated
   to its `(`.

2. **`enterWrappedDelimitedListScope` ignores the line's actual starting
   column.** The scope pushes `printer.indent()` twice (= `2 * indentSize`)
   relative to the printer's *natural* indent. When a wrapped construct
   appears on a continuation line whose start column was bumped via
   `printCont()` (ternary `?`/`:`, binary chains, manual continuation
   padding), the scope under-shoots: subsequent lines auto-indent to the
   natural-indent + 2, not to the continuation column + 2.

Lambda parameters already escape this trap (`PrincePrettyPrinterVisitor.printLambdaParameters`
captures `openParenStartColumn` and uses `padToColumn0(openParenStartColumn)`
before `)`). That is the precedent we generalize from.

### At-risk sites (delimiter-pair `(...)` with optional own-line closer)

| Site | File | Behavior today |
|------|------|----------------|
| `printArguments` (method calls, `new` expressions, enum-constant args, explicit-constructor invocations, single-member annotations[a]) | `PrincePrettyPrinterVisitor.java:722` | Bug: own-line `)` lands at popped indent. |
| `formatConstructor` params | `DeclarationFormatter.java:171` | Bug latent: works only because constructors are always declaration-level. |
| `formatMethod` params | `DeclarationFormatter.java:224` | Same. |
| `formatRecord` components | `DeclarationFormatter.java:276` | Same. |
| `printLambdaParameters` `(...)` | `PrincePrettyPrinterVisitor.java:1254` | **Already fixed**: pads to `openParenStartColumn`. |
| `try (...)` resources header | `PrincePrettyPrinterVisitor.java:404` | Latent: `try` is statement-level so opener is on its line. |
| `extends`/`implements`/`permits` clause | `DeclarationFormatter.java:351` | Different shape; closer is a `{` for the class body, not `)`. |
| `NormalAnnotationExpr` `(...)` with comments | `PrincePrettyPrinterVisitor.java:646` | Possibly affected when annotation is nested mid-line, but rare in practice. |
| Array initializer `{...}` and switch arms | various | Different delimiter; do not currently emit own-line `)`. |

[a] `printArguments` is dispatched from `MethodCallExpr` and (via
`super.visit`) `ObjectCreationExpr`, `ExplicitConstructorInvocationStmt`,
`EnumConstantDeclaration`. So a single fix here covers all expression-call
sites.

## Proposed solution (three-stage)

The fix is staged so each stage is independently reviewable, regression-safe,
and small enough to land with goldens regenerated cleanly.

### Stage 1 — nested closer placement follows opener relationship

**Rule (refined Rule 8 in `docs/canonical-formatting-rules.md`):**

> Nested wrapped-call closer behavior is determined by whether opener tokens are
> co-line or line-separated:
>
> - **Co-line nested openers** (e.g. `methodA(methodB(`):
>   - `closingParenOnNewLine=true` → emit a dedicated closer line aligned to the
>     outer opener start, compacting closer run as `));` / `)));`.
>   - `closingParenOnNewLine=false` → emit compact closer run inline on the last
>     content line.
> - **Line-separated nested openers** (outer `(` and inner `(` start on different
>   physical lines):
>   - `closingParenOnNewLine=true` → keep separate closer lines, each aligned with
>     its matching opener.
>   - `closingParenOnNewLine=false` → keep compact inline closer run (`));`) at
>     end of the inner call's final content line when nesting closes together.

**Detection.** Capture two values at the moment of printing `(`:

```java
int openerColumn = ctx.column();              // column where ( will sit
int lineIndentColumn = ctx.lineIndentColumn(); // current printer auto-indent column
boolean midLineOpener = openerColumn > lineIndentColumn;
```

`lineIndentColumn()` is a new helper on `LayoutContext` that returns either
`(printerIndentLevel) * indentSize` (spaces mode) or the equivalent for tabs.
For tab mode, comparison is against the auto-indent prefix length the printer
emits at the start of the next blank line.

**Apply:**

```java
if (fmt.closingParenOnNewLine() && wrapped) {
    if (nestedOpenersAreColine) {
        ctx.println();
        ctx.padToColumn0(outerOpenerStartColumn); // compact closer run target
    } else {
        ctx.println();
        ctx.padToColumn0(thisOpenerStartColumn); // separate aligned closer
    }
}
ctx.print(")");
```

**Effect on the repro cases.**

| Case | Before | After Stage 1 |
|------|--------|---------------|
| 0 | 2× stacked `)` at col 8 | co-line nesting: dedicated closer line `));` aligned with `methodA(` start |
| 2 | 3× stacked `)` at col 8 | co-line nesting: dedicated closer line `)));` aligned with outer call start |
| 3 | `)` at col 8 below `?`/`:` | branch closer joins outer closer on aligned closer line |
| 4 | 2× stacked `)` at col 8 | dedicated closer line: `));` aligned with outer call start |
| 5 | unchanged (top-level wrapped call) | unchanged |
| 6/7 | 3× stacked + args at col 16 | co-line closers compacted; line-separated closers remain separately aligned; args still misindented (Stage 2) |

**Sites to update (Stage 1).** All "delimiter-pair-with-optional-own-line-`)`"
sites listed in the table above. Constructors / methods / records keep
existing behavior because their opener is on the declaration's own line, so
`midLineOpener` is naturally `false`. Lambda parameters already align via
`padToColumn0`; rewrite to use the same helper for consistency, but keep
behavior identical.

### Stage 2 — wrapped scope must respect the line's actual start column

**Rule (refined Rule 3 / canonical-formatting-rules.md):**

> When a wrapped delimited list opens on a continuation line (i.e. on a line
> whose effective start column is greater than the printer's auto-indent
> column due to `printCont()` / ternary / binary continuation), its
> continuation lines indent to *(effective line start column) + `2 * indentSize`*,
> preserving canonical Rule 3 for delimited-list continuations, **not** to the
> printer's natural indent + `2 * indentSize`.

**Implementation.** `LayoutContext.enterWrappedDelimitedListScope()` becomes
column-aware:

```java
void enterWrappedDelimitedListScope() {
    // Canonical Rule 3: delimited-list continuation budget stays 2 * indentSize.
    // The target column is based on the line's effective start column, not natural indent.
    int target = currentLineStartColumn() + fmt.continuationIndentSize();
    // Use indentWithAlignTo so subsequent println() auto-indents to `target`.
    indentWithAlignToSafe(target);
    indentWithAlignToSafe(target); // pair with two unindent()s on exit (preserves existing depth invariant)
    wrappedDelimitedListScopeDepth++;
}
```

The double `indentWithAlignToSafe` keeps the existing two-level pop in
`exit…Scope()` (no changes to the depth counter or the scope-active flag
used by `printCont`). If `indentWithAlignTo` proves awkward to pair, an
alternative is to record the saved indent on a stack inside `LayoutContext`
and replay it on exit.

**Effect on the repro cases.**

Case 6 (ternary with inner wrapping) becomes:

```java
return methodA(
        longConditionExpressionForCheck
                ? methodB(
                        longArg1,
                        longArg2,
                        …
                        longArg6)
                : methodC(
                        longArg1,
                        …
                        longArg6));
```

— args clearly nested inside their `methodB(`/`methodC(`, closers collapse onto
one aligned closer line (Stage 1), and the outer `methodA(` breaks before its single ternary arg
(see Stage 3 below).

Stage 2 alone does not break before single args; it only fixes the *column*
where args land when the inner construct does wrap.

### Stage 3 — break before a single wrapping arg unless it is a chain receiver

**Currently** (TDR-017): when a method call has a single argument and that
argument needs to wrap, the formatter prints the argument inline so chain
receivers (`new X("""…""".formatted(...))`) keep their Rule 7 column math.
That carve-out is correct **only for chain receivers**. For everything else
(nested method calls, ternaries, binary chains, `new` expressions, array
creations), keeping the inner expression inline forces a mid-line opener and
either bad continuation indent (Stage 2 territory) or extra closer noise.

**Rule.** In `ArgumentListFormatter.printCommaSeparatedExprs`, the
`args.size() == 1 && argsNeedWrap` path becomes:

```java
if (args.size() == 1) {
    Expression only = args.get(0);
    if (shouldBreakBeforeSingleWrappedArg(only)) {
        ctx.println();
        ctx.printCont();
    }
    ctx.accept(only, arg);
    return;
}
```

with

```java
private boolean shouldBreakBeforeSingleWrappedArg(Expression e) {
    if (e instanceof MethodCallExpr mc && mc.getScope().isPresent()) return false; // TDR-017 chain
    if (e instanceof LambdaExpr lambda
        && lambda.getBody() instanceof BlockStmt) return false;                    // existing handling
    return true;
}
```

When this returns `true`, the outer `printArguments` must **also** push the
wrapped-list scope (currently it does not for single-arg). That keeps the
scope/closer accounting symmetric.

Together with Stage 1+2, this collapses Case 6 to the idiomatic shape above.

## Canonical-rules alignment notes (pre-implementation checks)

- **Stage 2 ambiguity resolved by canonical Rule 3.** Keep delimited-list continuation
  budget at `2 * indentSize`; Stage 2 only changes the baseline column from natural
  block indent to the line's effective start column.
- **Stage 1 requires a Rule 8 refinement (already planned).** Current canonical Rule 8
  says own-line closer at opener indentation, but does not define co-line-vs-line-separated
  nested closer behavior. Stage 1 refines Rule 8 to encode this distinction.
- **Exact closer placement for Stage 1.**
  - For `methodA(methodB(` with `closingParenOnNewLine=true`, emit `));` on a
    newline aligned with the start column of `methodA(`.
  - When `methodA(` and `methodB(` are line-separated and
    `closingParenOnNewLine=true`, emit separate closer lines aligned to each opener.
  - For `closingParenOnNewLine=false`, emit compact nested close as `));` at end
    of the final content line where the nested call finishes.

## Tests

A new asserting test file (replacing the probe):
`modules/core/src/test/java/io/princeofspace/internal/ClosingParenAlignmentTest.java`,
co-located with the internal classes it exercises.

Required cases (each with an idempotency assertion per Rule 1):

1. `methodA(methodB(arg1..argN))` — no stacked `)`.
2. Three-deep nest.
3. `methodA(cond ? methodB(...) : methodC(...))` where each branch's args
   wrap.
4. `methodA(new Outer(arg1..argN))` (object-creation as inner wrap).
5. Method chain receiver: `methodA("""text""".formatted(x, y, z))` — must
   keep current TDR-017 layout (chain Rule 7).
6. Top-level wrapped call (single-level): closer remains on its own line.
7. Method declaration with wrapped params: closer remains on its own line.
8. Lambda with wrapped params: layout unchanged.
9. Try-with-resources with multi-resource: layout unchanged.

Showroom goldens (24 files) regenerate via:

```
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
python3 scripts/generate-compare.py
```

`WrappingFormattingTest`, `RuleUniformityTest`, and the eval harness
(`OptionalRealWorldCheckoutFormatTest`) must remain green. Expect golden
churn primarily in scenarios that cross-call (showroom 4, 5, 9, etc.) — diff
should be an unambiguous improvement.

## Documentation updates (must land in same PR)

- `docs/canonical-formatting-rules.md` — refine Rule 8 wording to cover
  mid-line openers; add a note to Rule 3 about column-aware wrapped-list
  scope.
- `docs/formatting-rules.md` — add a small example showing nested call
  wrapping under `closingParenOnNewLine=true`.
- `docs/technical-decision-register.md` — new TDR entry "Closing-delimiter
  alignment for nested wrapped constructs"; supersede TDR-017 in the
  relevant scope.
- This file (`docs/implementation-plan-stacked-closers.md`) — delete or
  move once the work lands.

## Risk and mitigation

| Risk | Mitigation |
|------|------------|
| Showroom churn destabilizes downstream consumers (Spotless integration, IDE plugin) | Stage land separately so each diff is reviewable; eval harness against Guava + Spring (TDR-009) gates the merge. |
| Stage 2 changes wrap-scope math globally → could regress unrelated wrapped lists | Keep change localized to `enterWrappedDelimitedListScope`; assert via `RuleUniformityTest` that all list-like constructs still align Rule 5. |
| Stage 3 over-aggressively breaks before single args | Limit to expressions where `argsNeedWrap` is already true; chain-receiver carve-out preserves TDR-017. Add explicit tests for `new X("""…""".formatted(...))` and `single("""…""")`. |
| Tab-indent users see column math drift | `padToColumn0` already handles tabs; reuse it. Add a test combining tabs + nested calls. |
| Idempotency regression | Each stage's tests assert `format(format(x)) == format(x)`; existing `IdempotencyFuzzTest` provides broad coverage. |

## Sequencing

1. [x] Land Stage 1 + Stage 1 tests + Rule 8 doc refinement. This alone fixes
   Cases 0, 2, 3, 4 and most user-visible reports. ~1 small PR.
2. [x] Land Stage 2 + Rule 3 doc refinement + Case 6/7 args-indent tests. ~1
   small PR; updates `LayoutContext`.
3. [ ] Land Stage 3 + supersede TDR-017's single-arg carve-out, narrowing it to
   chain receivers only. ~1 small PR; updates `printArguments` /
   `printCommaSeparatedExprs`.

After all three stages, retire this plan doc.
