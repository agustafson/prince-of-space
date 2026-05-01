# Code Review Suggestions — Prince of Space

Deep-analysis pass conducted 2026-04-30. Findings are grouped by severity and area. Each item cites file paths and line numbers and is intended to be discussed before action — many are judgment calls, not directives.

Legend: **[BUG]** likely defect • **[INC]** inconsistency • **[IMP]** improvement • **[DOC]** documentation gap • **[QUAL]** code quality / maintainability • **[DONE]** completed (commit hash in trailing parens).

Project policy: minimum supported Java release is **8**. Findings related to Java 1–7 syntax/semantics are out of scope and are either marked [INC] (dead surface to remove) or omitted.

---

## Findings

### Public API & Configuration

#### 1. **[INC]** Inconsistent null-handling across public entry points
- `Formatter` constructor (`Formatter.java:33`) uses `requireNonNull(config, "config")` → `NullPointerException`.
- `FormatterConfig` compact constructor (`FormatterConfig.java:42-49`) throws `IllegalArgumentException` for null record components.
- `Formatter.format(String)` (`Formatter.java:54`) does **not** null-check `sourceCode`. It will surface as an NPE deep inside `JavaParser`.
- `Formatter.format(String, Path)` does not null-check either argument; a null Path silently renders as `"null: ..."` in error messages.

**Suggestion:** Pick one convention for required public-API arguments (NPE via `Objects.requireNonNull` is the JDK norm) and apply it uniformly to `sourceCode` and `filePath`. `FormatterConfig` already throws `IllegalArgumentException` for null record components — that's at odds with the JDK convention; consider switching to NPE for null and reserving `IllegalArgumentException` for "wrong but non-null" values.

#### 2. **[INC]** Missing `formatResult(String, Path)` overload
`Formatter.java:85` adds path-prefixed diagnostics for the throwing API, but no equivalent exists for the non-throwing `formatResult` API. Callers of the sealed-result API who want path context have to reformat the failure message themselves. Either drop the throwing overload or add the non-throwing one for symmetry.

#### 3. **[QUAL]** `FormatterConfig.Builder` defers null-validation to `build()`
The builder setters (e.g. `indentStyle(IndentStyle)` at `FormatterConfig.java:103`) accept null and only fail at `build()`. Stack traces point to construction, not the setter that supplied the bad value. Either null-check in each setter or annotate parameters with `@NonNull` (JSpecify) so NullAway reports the call site at compile time.

#### 4. **[DOC/QUAL]** Builder defaults duplicate semantic intent
Default values are encoded both in the builder field initializers (`FormatterConfig.java:87-93`) and in the architecture docs. There's no single source of truth — the docs can drift. Consider hoisting defaults to named constants on the record (e.g. `DEFAULT_LINE_LENGTH = 120`) and referencing them from both the builder and the documentation. Currently the default `JavaLanguageLevel.of(17)` appears in three places: builder, docs/architecture.md, and README.

#### 5. **[QUAL]** `FormatResult.NonConvergent` exposed as a generic `Failure`
A non-convergent format almost always indicates a *formatter bug*, not user input being invalid. Lumping it with `ParseFailure` and `EmptyCompilationUnit` means consumers (e.g. CLI, IDE plugin) cannot easily distinguish "your code is broken" from "the formatter is broken." Consider either a separate sealed branch or distinct treatment in user-facing tools (CLI should probably exit with a different code so it can be reported as a bug).

#### 6. **[INC]** `FormatterConfig.continuationIndentSize()` is a public method but undocumented as a knob
The Javadoc on `continuationIndentSize()` (`FormatterConfig.java:52-63`) describes a *derived* value — fine — but it is not listed in the README, architecture.md, or canonical-formatting-rules.md as part of the public API surface. If consumers should not call it, mark it `@deprecated` or move the calculation `internal/`. If they should, document it in the public option table.

### Engine & Parser Integration

#### 7. **[DONE]** `JavaParser` is constructed on every pass
`FormattingEngine.java:124` calls `new JavaParser(parserConfig)` inside `singlePassFormat`. The convergence loop runs up to 12 times by default. Each construction allocates lexer state and (in some JavaParser versions) primes a configuration cache. Hoist to a field — the parser is otherwise stateless across `parse()` calls within the same configuration.

**Resolution:** Hoisted `JavaParser` to a `final` field on `FormattingEngine`, initialized once in the constructor. `singlePassFormat` now reuses the field instead of allocating per pass. The previous `parserConfig` field was demoted to a constructor-local since only the parser needed it. Thread-safety: `JavaParser` is not thread-safe, but neither was `prettyPrinter` or `LexicalPreservingPrinter.setup` — the engine was already single-thread-per-`Formatter`. Multi-thread coverage is tracked separately as #55.

#### 8. **[DONE]** Negative `prince.maxConvergencePasses` and `NumberFormatException` are silently absorbed
`FormattingEngine.java:99-109`:
- A non-numeric value falls back to default with no log entry.
- A negative value is clamped via `Math.max(0, …)` with no log entry.

In both cases the user has set a system property explicitly and it's being ignored. At minimum, log at WARNING (use the existing `LOG` field).

**Resolution:** Introduced `interpretMaxConvergencePasses(String)` (used from static init and covered by unit tests). Non-integer values log `WARNING` and fall back to the default pass budget; negative values log `WARNING` and clamp to `0`. Replaced silent `Math.max`/`catch` with explicit branches.

#### 9. **[DONE]** `@SuppressWarnings("ConstantConditions")` placed at method level
`FormattingEngine.java:141` suppresses on the whole `transform(CompilationUnit)` method to silence one varargs-null call. Move the suppression to a local variable or use a typed `Void v = null` to remove the need for suppression entirely.

**Resolution:** Moved the suppression to a local `Void visitorArg = null` inside `transform` so only that assignment is annotated.

#### 10. **[DONE]** Legacy Java 1–7 mapping is dead surface (project supports Java 8+)
`JavaParserLanguageLevels.java:63-74` retains a `LEGACY_RELEASE_1`–`LEGACY_RELEASE_7` switch. Since the project only supports Java 8+, the legacy arms (and the `MIN_SUPPORTED_LEVEL = 1` floor in `JavaLanguageLevel`) are unused surface that misleads readers and complicates input validation. Remove the legacy arms; raise the minimum to 8; tighten the public Javadoc accordingly.

#### 11. **[QUAL]** `transform()` doc-string is misleading
`FormattingEngine.java:142` only mentions `BraceEnforcer` and `AnnotationArranger`, but `AnnotationArranger` is currently a no-op (see `AnnotationArranger.java:15`, an empty class extending `ModifierVisitor`). Either remove `AnnotationArranger` until it does something, or document why an empty hook is registered.

### Pretty-Print / Blank-Line Stage

#### 12. **[DONE]** `BlankLineNormalizer.isImportLine` only matches `"import "` (space-separated)
`BlankLineNormalizer.java:135-143` requires a literal space after `import`. JLS allows any whitespace between `import` and the qualified name (`import\tjava.util.List;` is legal). Although JavaParser's pretty printer is unlikely to emit a tab there, if upstream output ever changes, the import-spacing preservation rule will silently break.

Replace the `regionMatches` guard with one that consumes any whitespace after the `t` in `import`, or compare against `"import"` followed by whitespace check.

**Resolution:** `isImportLine` now matches the `import` keyword and requires `Character.isWhitespace` after it (any JLS line terminator or space). Added `blankLineBetweenImports_preservedWhenWhitespaceAfterImportKeywordIsTab`.

#### 13. **[QUAL]** `BlankLineNormalizer` line-array growth is allocation-heavy
`BlankLineNormalizer.java:36-39` doubles the arrays in lock-step (`grow(lineStarts); grow(lineEnds);`). For a 1MB file with ~50-char lines you'd see at most a couple of grows; not a hot path. But the over-allocation strategy of `len / 40 + 1` plus doubling can over-allocate ~2x for a typical file. Consider using `ArrayList<int[]>` or allocating two buffers in one method (one int[] of length `2 * estimated`, paired indices `[2*i, 2*i+1]`) to halve the overhead.

### Visitor (PrincePrettyPrinterVisitor)

#### 14. **[DONE]** Two divergent `SwitchEntry` printers
`PrincePrettyPrinterVisitor.java` has both:
- `visit(SwitchEntry n, Void arg)` (line 1169) — used for `SwitchStmt` entries
- `printSwitchEntry(SwitchEntry, Void arg)` (line 1209) — used for `SwitchExpr` entries

The two paths handle the same construct differently:
- `printSwitchEntry` handles `STATEMENT_GROUP` by printing `:` then `return;` — **so colon-style entries inside a `switch` *expression* drop their statements entirely.** Although uncommon, JLS allows colon-style entries in switch expressions; this would silently lose code.
- The `, default` tail-label combination (`case A, default ->`) is only handled in `visit(SwitchEntry)`; switch *expressions* can have it too but lose it via `printSwitchEntry`.
- `when` guard layout differs subtly between the two.

**Suggestion:** Unify into one helper that handles both forms; route both `visit` overrides to it.

#### 15. **[BUG]** Text block `\"\"\"\n` newline is hard-coded
`PrincePrettyPrinterVisitor.java:1119-1121`:
```java
printer.print("\"\"\"\n");
printer.print(n.getValue());
printer.print("\"\"\"");
```
The `\n` is hard-coded but `PrettyPrinter.java:44` configures `END_OF_LINE_CHARACTER` to `\n`. Hidden coupling — if EOL ever becomes configurable, text blocks will mix line endings.

Also: `n.getValue()` for a `TextBlockLiteralExpr` returns the raw text; JavaParser's value extraction has had quirks across releases. Worth a dedicated round-trip test for text blocks containing leading-whitespace stripping edge cases.

#### 16. **[DONE]** `defaultVisit(Node, Void)` silently no-ops for unsupported types
`PrincePrettyPrinterVisitor.java:269-275` only routes `BinaryExpr` and `MethodCallExpr`. Any other node passed to `defaultVisit` produces no output. A future caller could pass a different type and get silent failure. Throw `IllegalArgumentException` on unsupported types.

**Resolution:** `defaultVisit` now throws `IllegalArgumentException` with the unexpected node class; `LayoutContext.acceptDefault` Javadoc references the supported types.

#### 17. **[QUAL/BUG]** Mutating the AST during printing is fragile
Multiple paths invoke `comment.remove()` after printing (`PrincePrettyPrinterVisitor.java:259, 334`; `BinaryExprFormatter.java:331`; `MethodChainFormatter.java:319`). The Javadoc explains the *why* (preventing duplicate orphan emission across passes), but this means:
- The CompilationUnit visible to the engine is *not* the same after one print as before.
- Any consumer that wants to inspect the AST post-format gets a comment-stripped tree.
- The convergence loop in `FormattingEngine` re-parses each pass, so mutations don't accumulate across passes — but inside a single pass, the order of comment removal vs further visiting matters.

Consider deep-cloning the CU once at the top of `FormattingEngine.printAfterTransform` and mutating the clone; that decouples printer side-effects from the parsed input.

#### 18. **[INC]** Two ways to ask for column: `column()` private vs `ctx.column()`
`PrincePrettyPrinterVisitor.java:623-625` defines a private `column()` while several places use `ctx.column()`. Pick one and use it throughout. Same for `printCont()` (private) vs `ctx.printCont()`.

#### 19. **[INC]** Blank-line preservation differs between method body and lambda body
`PrincePrettyPrinterVisitor.java:404-414` (BlockStmt body) checks `hasInterveningComment` and `currentStatementPrintsCommentBeforeCode` before deciding to preserve a blank line.
`PrincePrettyPrinterVisitor.java:1056-1062` (lambda block body) only checks raw `prevEnd`/`curStart` line gap.
A user-supplied blank line between a comment and the next statement may be preserved in one place and stripped in the other. Unify.

#### 20. **[QUAL]** Deeply nested visitor logic in `visit(VariableDeclarator)`
`PrincePrettyPrinterVisitor.java:962-1020` is hard to follow: 5-deep lambda chain plus several width-check predicates evaluated unconditionally. Extract the array-dimension-tail-emission to a named helper (`printAdditionalArrayDimensions`) and the initializer-break decision to a single `decideInitializerLayout(init)` returning an enum (`INLINE`, `BREAK`, `BREAK_AFTER_EQUALS`).

### Helper formatters

#### 21. **[QUAL]** Massive duplication between `printParametersList` and `printParametersListForLambda`
`ArgumentListFormatter.java:270-322` and `:329-379` are 50+ lines of nearly identical code, differing only in the continuation-print mechanism. Parameterize on a `ContinuationStrategy` (a `Runnable` or method ref).

#### 22. **[BUG]** `paramsFlatWidth` / `typeArgumentsFlatWidth` / `typeParametersFlatWidth` use `toString()`
`ArgumentListFormatter.java:242-253, 387-398, 464-475` all sum `p.toString().length()`. For nodes that contain comments or whose `toString()` includes pretty-printing artifacts (newlines), this *over-counts*, causing premature wrapping. Delegate to `WidthMeasurer.flatWidth(...)` (which has special-case logic for many node types). Note the same bug appears in `TypeClauseFormatter.java:38, 243, 320`.

#### 23. **[INC]** Inconsistent reservation widths in `*NeedWrap` predicates
`argsNeedWrap` (line 50) and `paramsNeedWrap` (line 256) reserve **1** char (the `(`).
`typeParametersNeedWrap` (line 401) and `typeArgumentsNeedWrap` (line 478) reserve **2** chars (`<` and `>`).
The `(` predicates don't reserve for the closing `)`. The constants implicitly assume the closer is irrelevant for line-length purposes for parens but relevant for angles. Document or fix.

#### 24. **[BUG]** `WidthMeasurer.lambdaHeaderWidth` reachable dead branch
`WidthMeasurer.java:144-146`: the `else` branch (`!isEnclosingParameters() && size != 1`) is unreachable — Java syntax requires multi-param lambdas to enclose params in parens. Either delete or explicitly throw.

#### 25. **[DONE]** `WidthMeasurer.expressionWidth(ObjectCreationExpr)` ignores type arguments
`WidthMeasurer.java:86-93`: `new HashMap<String, Integer>()` is measured as `new HashMap()` (off by `<String, Integer>` length). This causes the formatter to under-estimate widths for parameterized constructors and miss wrap opportunities.

Resolution: the example in the finding turned out to be already correct — `getType().toString()` for a `ClassOrInterfaceType` already includes the diamond `<X, Y>` arguments, so `new HashMap<String, Integer>()` was measured at 30 chars (matches actual). But two related cases really were under-measured:
- **Inner-class scope** (`outer.new Inner<String>()`): `getScope()` was ignored (off by `outer.` = 6).
- **Explicit prefix type arguments** (`new <String> Foo()`): `getTypeArguments()` was ignored (off by `<String> ` = 9).
Reworked the branch to count scope width (when present), explicit prefix type arguments (when present), the type's own toString (which carries diamond/declared args), the parens + arg list, and the optional anonymous body. Added four targeted `WidthMeasurerTest` cases (standard parameterized, explicit prefix type-args, inner-scope, diamond) that all assert width equals `toString().length()`.

#### 26. **[BUG]** `WidthMeasurer.expressionWidth(LambdaExpr)` non-block, non-expression body NPE
`WidthMeasurer.java:151`: `lambdaExpr.getBody().asExpressionStmt().getExpression()` will throw `IllegalStateException` for any body that's neither a `BlockStmt` nor an `ExpressionStmt`. A defensive `instanceof` check (or a fallback to `e.toString().length()`) would be safer.

#### 27. **[QUAL]** Massive duplication in `BinaryExprFormatter.format`
`BinaryExprFormatter.java:41-84` (AND/OR) and `:85-129` (BINARY_AND/BINARY_OR/XOR) are 45-line copy-pastes of the same algorithm. Extract a single helper that takes the operator group as a parameter.

#### 28. **[QUAL]** Long method names
`BinaryExprFormatter.printExprWithTrailingCommentAfterWithMethodChainContinuationIndent` (70 chars) and similarly verbose names are present in `MethodChainFormatter`. Hard to scan in code review and navigation. Most could lose the `ContinuationIndent` suffix if a comment explains the side-effect.

#### 29. **[BUG]** Cloning operands strips parent context
`BinaryExprFormatter.java:437-439` clones an Expression and re-prints it. The clone has no parent, so any visitor logic that uses `findAncestor`/`getParentNode` against the cloned subtree gets `Optional.empty()`. The same pattern is present in:
- `PrincePrettyPrinterVisitor.java:488-493` (try-with-resources resource clone)
- `MethodChainFormatter.java:307-310, 314-324` (`printExpressionWithoutOwnComment`, `printArgumentsWithoutComments`)
For most expression types this is harmless, but `VariableDeclarator.visit` (line 966) and possibly other visitors *do* use ancestor lookup. If a cloned subtree ever contains those nodes, layout silently degrades.

#### 30. **[BUG]** `BinaryExprFormatter.format` fall-through prints comments twice
`BinaryExprFormatter.java:39-40` prints orphan comments and the binary's own comment at the *top* of `format()`. For unrecognized operators the method falls through to `ctx.acceptDefault(n, arg)` (line 177), which calls `super.visit(n, arg)` — and JavaParser's default visitor will also print those same comments, producing duplicates.

#### 31. **[DONE]** `MethodChainFormatter.lambdaHeaderWidth` uses `NodeList.toString()`
`MethodChainFormatter.java:175, 179`: `2 + lambda.getParameters().toString().length()`. `NodeList.toString()` returns `"[a, b]"` (Java's default `AbstractCollection.toString()`), so width is over-counted by ~2. Use `WidthMeasurer.commaSeparatedParameterWidth(...)` or sum element widths directly.

Resolution: bumped `WidthMeasurer.commaSeparatedParameterWidth` from `private` to package-private and switched the two call sites in `MethodChainFormatter.lambdaHeaderWidth` to use it. The `+2` for the surrounding parens stays; the over-count was the `[]` from `AbstractCollection.toString()` doubling up with that `+2`. Existing core tests + showroom goldens all still pass — the 2-char correction did not change any current wrap decision but removes a class of phantom over-wrapping from chain decisions involving multi-parameter lambdas.

### Type clause / declaration / string literal formatters

#### 32. **[DONE]** Off-by-one in keyword-width constants
`TypeClauseFormatter.java`:
- `INLINE_THROWS_KEYWORD_WIDTH = 7` for `" throws "` — actually 8 chars.
- `INLINE_IMPLEMENTS_KEYWORD_WIDTH = 12` for `" implements"` — actually 11 chars.
- `INLINE_EXTENDS_KEYWORD_WIDTH = 8` for `" extends"` — 8 chars (correct).
- `INLINE_PERMITS_KEYWORD_WIDTH = 9` for `" permits "` — 9 chars (correct).

The comment text describes the strings, but two of the four constants are wrong. Most impacts are small (one column off when deciding to wrap) but the inconsistency is real.

Resolution: traced each constant against the actual print sequence — every keyword path emits `" KEYWORD"` (no trailing space) and then `printInlineTypeClauseList` emits its own leading `" "` before the type list. So the width contribution is always `1 + keyword.length() + 1` (= " KEYWORD ") plus the type list. Re-verified:
- `" extends "` = 9 (was 8 — under by 1).
- `" implements "` = 12 (already 12 — correct; the original comment text `" implements"` was misleading but the value matched the actual print + trailing space).
- `" permits "` = 9 (already 9 — correct).
- `" throws "` = 8 (was 7 — under by 1).
Bumped EXTENDS to 9 and THROWS to 8; updated all four comments to show the actual `" KEYWORD "` strings (with trailing space) for consistency. Core tests + showroom goldens unchanged — none of the formatter's existing tests sat exactly on the boundary the constants moved.

#### 33. **[INC]** `printPermitsClause` has a NARROW guard `extends`/`implements` lack
`TypeClauseFormatter.java:170-171` blocks inline rendering when `wrapStyle == NARROW` even if the clause fits. `printExtendsClause` and `printImplementsClause` accept inline rendering in all wrap styles when it fits. Per Rule 5 ("WrapStyle must be construct-uniform"), all three should behave identically. Either add the guard to extends/implements or remove it from permits.

#### 34. **[QUAL]** Massive duplication: `printExtendsClause` / `printImplementsClause` / `printPermitsClause` / `printThrowsClause`
`TypeClauseFormatter.java:56-198, 257-300` — 4 nearly identical methods. Extract a parameterized `printTypeClause(keyword, types, wrapBehavior)`.

#### 35. **[DONE]** `formatRecord` uses ad-hoc `implements` rendering
`DeclarationFormatter.java:290-299` writes the implements clause manually with `ctx.print(" implements ")` and a comma-separated loop, bypassing `typeClauseFormatter.printImplementsClause` entirely. A record with a long implements list will not wrap. (Also misses `permits`-aware logic if a future record gains `permits`.)

#### 36. **[DONE]** `formatEnum` ignores `typeClauseWrapped` when emitting `{`
`DeclarationFormatter.java:388-392`: after `printImplementsClause` the code unconditionally emits `" {"`. If implements wrapped to a new line, the `{` lands inline at the wrong column. `formatClassOrInterface` correctly tracks `typeClauseWrapped` (line 349) — enum should do the same.

#### 37. **[DONE]** Empty enum gets a blank body line
`DeclarationFormatter.java:392-407` always prints `{` then `\n` then `}`. So `enum E {}` becomes:
```
enum E {
}
```
Canonical Rule 5 says "Empty enums remain `enum E { }` compatible with Rule 9 (no blank lines inside empty blocks beyond what the formatter already coalesces)." Current output adds an internal newline; either the rule should be revised or the code should emit `enum E {}` for the empty case.

#### 38. **[QUAL/DOC]** Mysterious Java 8 carve-out for compact empty methods
`DeclarationFormatter.java:251-262`: `modernCompactEmptyMethod` requires `level != 8`. There's no comment about *why* Java 8 is excluded. If this is to avoid a parser-rendering quirk, please document. If not, remove the guard.

#### 39. **[QUAL]** Three copies of "wrap params, emit `)`"
`formatConstructor`, `formatMethod`, `formatRecord` each have the same 14-line `paramsWrapped` block (`DeclarationFormatter.java:178-192, 231-245, 275-289`). Extract.

#### 40. **[DONE]** `printNormalizedLeadingBlockComment` discards internal blank lines
`StringLiteralFormatter.java:138-156` skips empty lines (`if (trimmed.isEmpty()) continue;`). If a user's leading block comment contains intentional blank separator lines, they will be silently removed.

**Resolution:** introduced a `pendingBlank` flag so internal blank lines between content lines are preserved as bare ` *` separator lines; trailing blanks remain trimmed. Regression test in `CommentPreservationTest.leadingBlockCommentInArrayInitializer_preservesInternalBlankLines`.

#### 41. **[QUAL]** Magic number `WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING = 4`
`StringLiteralFormatter.java:34` — assumes any code chunk lives within 4 block indents. Deeper nesting under-allocates string-chunk budget. No comment explains why 4 vs 6 vs 8.

### Comment, ArrayInitializer, LayoutContext, BraceEnforcer, PrettyPrinter

#### 42. **[QUAL]** `record CommentUtils()` is an unusual idiom
`CommentUtils.java:25` declares `record CommentUtils() { … }` with no components. Records are intended for value carriers, not stateless helpers. There are 21 instance methods that have no use of `this`. Either:
- Make it `final class CommentUtils { private CommentUtils() {} }` with `static` methods, or
- Convert all helpers to `static` and call them as `CommentUtils.foo(…)`.
This affects readability — every reader must do a double-take to figure out why a record has methods.

#### 43. **[BUG/DOC]** Doubled Javadoc blocks on the same method in `CommentUtils`
`CommentUtils.java:175-180`, `:254-259`, `:287-292`, `:317-322` each have two `/** … */` blocks immediately above the same method:
- Lines 175-179 explain the rationale; lines 180 is a one-liner. Java attaches the **second** one as the canonical Javadoc, and the first becomes an orphan that JavaParser will preserve but tools (IDE Quick Doc, javadoc tool) may render only the lower one. Worse, when running this very project's formatter on its own source, the orphan would be re-attached and potentially reordered.

Either merge into a single block, or hoist the rationale into a class-level comment.

#### 44. **[QUAL]** `firstLineOrBlockCommentPrintedBeforeExpression` uses `orElseThrow()` inside a stream comparator
`CommentUtils.java:115-116` uses `comment.getRange().orElseThrow()` inside a `Comparator.comparingInt`. The earlier filter `isCommentBeforeExpression` already requires range to be present, so the throw is unreachable today — but a future filter change could leak `Optional.empty()` past it and crash with no message. Cache the range when filtering, or use a safe extractor.

#### 45. **[DONE]** `ArrayInitializerFormatter` does not preserve interior comments inline
`ArrayInitializerFormatter.java:98-106` walks `n.getValues()` calling `ctx.accept(expr, arg)`, but never invokes `ctx.printOrphanCommentsBeforeThisChildNode(expr)`. So a comment that appears *between* two array literals (`{a, /* sep */ b}`) may be dropped or misplaced when the array prints inline. The multi-line path (`printTallInitializer`) has the same issue.

**Resolution:** the inline path cannot survive interior comments because the default block-comment visitor emits a trailing newline (this caused infinite oscillation between inline and tall layouts). Now `ArrayInitializerFormatter` detects any value with a leading line/block comment and forces a tall layout (one element per line). In tall mode, leading comments are emitted via the new shared `LayoutContext.printNormalizedBlockComment` (block) or a normalized line-comment printer, then stripped from the AST so the visitor does not re-print them. Also extracted the duplicated multi-line block-comment printer from `StringLiteralFormatter` to `LayoutContext`. Regression test in `FormattingEngineTest.arrayInitializerWithInteriorBlockComment_convergesToTallLayout` plus shape assertions in `CommentPreservationTest`.

#### 46. **[INC]** Asymmetric handling of "nested array initializer" vs top-level
`ArrayInitializerFormatter.java:40-94` — when the parent is also an `ArrayInitializerExpr` we align elements to `openBraceColumn + continuationIndentSize`; otherwise we use `printCont()`. The reasons are not documented. For a 2D array, this means inner braces get column-aligned padding while outer braces use plain continuation. There may be a deliberate visual reason but neither code nor docs explain it.

#### 47. **[QUAL]** Cross-module call for a width helper
`ArrayInitializerFormatter.java:38` calls `methodChainFormatter.argsFlatWidth(...)`. `MethodChainFormatter` is unrelated to array initializers; the only reason it owns this helper is historical. Move flat-width helpers to `WidthMeasurer` (or a `ListWidth` utility) so `ArrayInitializerFormatter` doesn't pull in `MethodChainFormatter` as a dependency.

#### 48. **[QUAL]** `LayoutContext.padToColumn0` documents a footgun rather than fixing it
`LayoutContext.java:67-89` carries a 12-line Javadoc explaining that `SourcePrinter` lazily emits indent and you must `print("")` to materialize it before measuring. This is genuinely subtle, but burying that into a method whose name (`padToColumn0`) gives no hint of the trap means callers who don't read the doc misuse it. A safer wrapper would force materialization unconditionally and offer a single `padToColumn(targetColumn)` API; the indent-style switch should be hidden inside.

#### 49. **[QUAL]** `LayoutContext` exposes raw scope mutators to delegates
`LayoutContext.java:121-127` re-exposes `enterWrappedDelimitedListScope`/`exitWrappedDelimitedListScope` from the visitor. Delegate code that forgets to pair them leaves visitor state corrupted across the rest of the print. Wrap as `try-with-resources` (`AutoCloseable`) or `withWrappedDelimitedList(Runnable)` to make scoping mistake-proof.

#### 50. **[INC]** `LayoutContext.acceptDefault` has a known-narrow target
`LayoutContext.java:172-174` calls `visitor.defaultVisit(node, arg)`, but `defaultVisit` only routes `BinaryExpr` and `MethodCallExpr` (see finding #16). Naming `acceptDefault` invites callers to use it for any node — and that yields silent no-op output. Either:
- rename to `acceptBinaryOrChain(...)` to match capability, or
- have `defaultVisit` fall through to `super.visit(node, arg)` for unknown types.

#### 51. **[INC]** `BraceEnforcer.super.visit(n, null)` discards the `arg`
`BraceEnforcer.java:24, 43, 51, 60, 69` all call `super.visit(n, null)` instead of `super.visit(n, arg)`. The `Void` arg is always null, so behavior is unchanged, but it telegraphs "I don't care what arg is" — and a future migration from `Void` to a real type-parameter would silently erase context. Use `arg`.

#### 52. **[BUG]** `BraceEnforcer` wraps statements without preserving originating range/comments
`BraceEnforcer.java:27, 34, 45, 53, 62, 71` synthesize a fresh `BlockStmt` around the original statement. The synthetic block has no source range; any later visitor logic that uses `getRange()` (e.g. `CommentUtils.hasCommentBetweenStatements`) returns "no opinion" for it. A pre-existing comment between an `if (x)` and its body could move or repeat across iterations. Test idempotency for `if (x) /* comment */ doX();`.

#### 53. **[BUG]** `PrettyPrinter` hard-codes `END_OF_LINE_CHARACTER` and `SPACE_AROUND_OPERATORS`
`PrettyPrinter.java:44-45`. Per Rule 1 ("FormatterConfig is the single source of truth"), every printer-affecting toggle should derive from `FormatterConfig`. Two reasons to fix:
- Cross-platform line endings (`\r\n` on Windows, project-specific) cannot be requested.
- `SPACE_AROUND_OPERATORS = true` is a stylistic decision baked into the printer rather than the config.
Move both to `FormatterConfig` knobs (or document why they are intentionally non-configurable).

#### 54. **[QUAL]** `AnnotationArranger` is dead code
Already noted in #11. Combined with the two-line comment in `AnnotationArranger.java:15`, the class adds a no-op transform pass to every format invocation. It also requires reading two javadoc paragraphs to understand why nothing happens. Delete or implement.

### CLI module

#### 55. **[DONE]** `Main` constructs a *single* `Formatter` shared across worker threads
`Main.java:113-123, 168-205` builds one `Formatter` and passes it to a fixed thread pool. `Formatter` itself is documented as "Stateless and thread-safe" (per the Javadoc on the public class), but the underlying `FormattingEngine` allocates a `JavaParser` per pass (finding #7) and calls `LexicalPreservingPrinter.setup` which mutates the AST. If any internal field (`tracker`, comment hooks, etc.) is non-thread-safe, two threads formatting concurrently will corrupt each other.

Actionable item: write a multi-thread test that runs `Formatter.format` from N threads against the same `Formatter`, hashes the output per-thread, and asserts each thread's hash matches the single-threaded result.

**Resolution:** Added `FormatterConcurrencyTest`, which runs `Formatter.format` across 8 threads × 10 iterations × 4 inputs concurrently and asserts every output matches the single-threaded reference. The first run reproduced a `ConcurrentModificationException` from the shared `FormattingEngine` (JavaParser/PrettyPrinter both hold mutable state). Fixed by switching `Formatter` to hold a `ThreadLocal<FormattingEngine>`: each calling thread gets its own engine the first time it formats, so JavaParser/PrettyPrinter state is never accessed concurrently. The Javadoc on `Formatter` was updated to make the thread-safety contract explicit.

#### 56. **[QUAL]** `Main` mixes verbose logging with race-y stderr writes
`Main.java:186-188` writes `r.path()` to `stderr` from the *main* thread as it joins futures, but if `verbose` is on and a worker also throws, the order of "file processed" vs "exception" lines is non-deterministic. Use a thread-safe log channel or buffer per result.

#### 57. **[BUG]** `--check` mode does not write but still reads every file
This is the documented behavior, but the doc on `--check` (`Main.java:56-58`) says "does not write files" — true — without warning that for very large repos `--check` is just as slow as the formatting pass. A common Spotless workflow runs `--check` in CI and the actual format locally; consumers may expect `--check` to short-circuit on first diff. Document the cost or add an `--early-exit` flag.

#### 58. **[QUAL]** Git-aware path collection silently falls back
`Main.java:222-237`: if `gitRoot == null && recursive`, falls back to `Files.walkFileTree`. If `gitRoot != null && !recursive`, it doesn't use git at all (uses `Files.list`). Inconsistent; either always defer to git when present or document the carve-out. Also `gitListedJavaFiles` runs `git ls-files` *twice* (tracked + untracked) per directory — acceptable but worth a comment.

#### 59. **[BUG]** `walkJavaFiles` ignores `node_modules` and dotfiles inconsistently
`Main.java:319-328` only skips `.git`, `build`, `target`. Common large directories like `node_modules`, `out`, `bin`, `.gradle`, `.idea`, `.vscode`, `dist` are walked. Recursive runs against monorepos can be very slow. Consider a richer exclude list or a `.princeignore` file.

#### 60. **[QUAL]** `BatchResult` carries the entire formatted source
`Main.java:206`: each future result holds the full formatted source until the main loop processes it. For a thousand-file batch, peak memory is O(N * file size). Stream results (e.g. via a `BlockingQueue<BatchResult>` and producer/consumer) or write inside the worker.

#### 61. **[BUG]** Config validation is duplicated and partial
`Main.java:148-151` catches `IllegalArgumentException` to render config errors. But picocli will *also* throw on bad enum input (e.g. `--wrap-style ZIGZAG`) and return a different exit code path than 2. Two distinct error formats result. Consider a single `Main.validate(config)` helper called before `runBatch` and a unified non-zero exit.

### Spotless module

#### 62. **[DONE]** `PrinceOfSpaceStep.formatterFunc` constructs a new `Formatter` per file
`PrinceOfSpaceStep.java:30`: `FormatterFunc.needsFile((unix, file) -> new Formatter(config).format(unix, file.toPath()))`. Spotless invokes the function per file, so `new Formatter(config)` runs hundreds or thousands of times per build, each allocating its `FormattingEngine` and downstream visitors. Cache the `Formatter` instance — `FormatterConfig` is the only state.

**Resolution:** moved the `new Formatter(config)` allocation out of the per-file lambda into the enclosing `formatterFunc(config)`. Spotless calls `formatterFunc` once per step and reuses the returned `FormatterFunc` for every file in the build, so a single `Formatter` (and its underlying `FormattingEngine` + parser config) now serves the entire run.

#### 63. **[INC]** `FormatterStep.create` requires `Serializable` config but the contract is undocumented in the API
`PrinceOfSpaceStep.java:25-27` asserts via Javadoc that the config "must be Serializable", but `FormatterConfig` (record) does not declare `implements Serializable`. Records are auto-Serializable only when all components are. Today the config consists of primitives, enums, and `JavaLanguageLevel`; if any future config knob is non-serializable (a `Path`, `BiFunction<…>`, etc.), Spotless will silently fail at use-time inside the build classloader. Add a unit test that round-trips `FormatterConfig.defaults()` through `ObjectOutputStream`.

### IntelliJ plugin module

#### 64. **[BUG]** `PrinceFormatRunner.format` skips the document modification when no formatter `Path` is available
`PrinceFormatRunner.java:39-41`: When `vf` is null or non-local (e.g. an in-memory PsiJavaFile), `nioPath` is null; falls through to `formatter.format(text)`. OK so far. But `JavaParser` warnings printed via `LOG` will then reference "<unknown>" rather than the file — making errors hard to debug. Pass `vf.getPresentableName()` or similar wrapped-Path object.

#### 65. **[DONE]** Format-on-save races with concurrent saves
`PrinceOfSpaceFormatOnSaveListener.java:21, 43-50`: `FORMATTING` is a `ThreadLocal<Boolean>`. IntelliJ may dispatch save-related listeners off the EDT in newer versions; per-thread re-entry guard does not protect against two distinct threads saving overlapping documents. Consider keying re-entry by `(documentId, threadId)`, or use a `ConcurrentHashMap.newKeySet()` of in-flight documents.

**Resolution:** Replaced the `ThreadLocal<Boolean>` re-entry flag with a `ConcurrentHashMap.newKeySet()` of in-flight `Document` instances. The check-and-add is now `IN_PROGRESS.add(document)` (atomic) wrapped in a `try { … } finally { IN_PROGRESS.remove(document); }`. Same-thread re-entry (the original case the ThreadLocal protected against — `setText` re-firing the listener) and cross-thread races (two off-EDT save dispatchers picking up the same document) are both blocked by the same primitive.

#### 66. **[DONE]** `PrinceOfSpaceProjectSettings.toFormatterConfig` is non-thread-safe under format-on-save
`PrinceOfSpaceProjectSettings.java:34-53`: reads `projectState.commonState` fields without synchronization. The settings UI mutates the same fields via `replaceState` (line 75-78). Under format-on-save, a long-running format and a settings apply can interleave reads/writes of `String indentStyle`, producing a mid-format style switch.

Use a defensive snapshot at the top of `toFormatterConfig` (clone `commonState` once) or copy fields onto a stack-local before reading.

Resolution: added `private final Object lock = new Object();` to `PrinceOfSpaceProjectSettings` and `PrinceOfSpaceGlobalSettings`. `toFormatterConfig` now snapshots state inside the synchronized block (via new `ProjectState.copy()` / `CommonState.copy()` helpers) and reads from the immutable snapshot outside the lock. All mutators (`loadState`, `replaceState`, `setFormatOnSave`, `getState`) acquire the same lock so reads cannot interleave with `XmlSerializerUtil.copyBean` field writes. `getState()` returns a snapshot copy so the IntelliJ serializer cannot read a field mid-mutation either. Project settings release their lock before delegating to global settings, so no deadlock is possible.

#### 67. **[INC]** Missing settings persisted in wrong scope
`PrinceOfSpaceProjectSettings.java:21-23` uses `StoragePathMacros.WORKSPACE_FILE` ("workspace") — meaning per-user workspace state, not committed to VCS. If a project intentionally pins a Prince-of-Space style, that style won't follow the project across machines. Consider switching to `$PROJECT_FILE$` or providing a `.idea/prince-of-space.xml` storage so checked-in settings are honored.

#### 68. **[QUAL]** Plugin XML lacks `<idea-version>` constraint
`plugin.xml:1-35`: no `<idea-version since-build="…"/>` attribute. JetBrains marketplace requires this; without it the plugin can be installed against very old IDEs that don't have the APIs used in code (`ActionUpdateThread.BGT` was added in 2022). Add a sensible floor.

#### 69. **[QUAL]** Magic check in `PrinceOfSpaceConfigurable.JAVA_LEVEL_DEFAULT`
`PrinceOfSpaceConfigurable.java:41` re-declares `JAVA_LEVEL_DEFAULT = 17`. The same default is also defined in:
- `Main.java:65` (`defaultValue = "17"`)
- `FormatterConfig` builder
- README
Consolidate (per finding #4) — `FormatterConfig.DEFAULT_JAVA_LEVEL` then everyone references it.

#### 70. **[BUG]** `PrinceOfSpaceState.CommonState.normalizeAfterLoad` clamps `indentSize` silently
`PrinceOfSpaceState.java:36-37`: a user with `indentSize = 100` sees their value silently clamped to 32 with no UI feedback. Either show an info popup the first time or surface in the settings panel.

### VS Code extension

#### 71. **[DEFER]** Concurrent invocations of `formatJavaSource` cannot share Java warm-up
`formatter.ts:37-68` spawns a fresh `java -jar` for every format call. JVM startup is ~300-700ms; for save-on-format on every keystroke or rapid edits this is painful. Consider a long-lived helper process (the CLI already supports `--stdin`) communicating over a length-prefixed protocol.

Resolution: deferred. The extension only registers `provideDocumentFormattingEdits` (no on-keystroke invocation), so each format is once per save/explicit command — startup cost, not per-keystroke. Adding a daemon protocol is a feature (new CLI mode, wire framing, process lifecycle) sized well beyond triage scope; tracked here as a known perf gap.

#### 72. **[DONE]** `formatJavaDocument` may race with document edits
`extension.ts:5-39` reads `document.getText()` once when computing offsets but again when applying edits via `document.positionAt(document.getText().length)`. If the user edits the document between the two reads (e.g. format-on-save fires during a fast edit), the replacement range will be wrong. Capture the text **once** and compute both positions from the same snapshot.

Resolution: in `extension.ts` capture `document.getText()` once into `sourceText`, send that snapshot to the formatter, and compute the replacement range from `sourceText.length`. The end-position can no longer drift relative to the text the formatter saw.

#### 73. **[QUAL]** Hard-coded `"prince-of-space-cli-*.jar"` glob
`formatter.ts:28`: pattern only matches the unversioned shadow JAR location. If a user installs the CLI to a system path or via a package manager, they must use `princeOfSpace.cliJar` setting. Document the discovery rule clearly in README, or also probe `$PATH` for a `prince-of-space` executable.

#### 74. **[DONE]** `cliFormatterArgs` does not pass `--java-version 0` safely
`cliArgs.ts:18-23` always passes `--java-version` even if the value is 0 or NaN (no validation). The CLI then surfaces an `IllegalArgumentException` to stderr. Validate config at the extension boundary so a misconfigured user gets a friendly error in VS Code.

Resolution: added `validateFormatterOptions(opts)` to `cliArgs.ts` returning a human-readable error string (or `null`). The validator currently checks `javaVersion`: must be a finite integer >= 8 (the minimum supported release per the recent Java 1-7 cleanup). `extension.ts` calls it right after building `opts` and surfaces any error via `vscode.window.showErrorMessage` with a `Prince of Space:` prefix, so a misconfigured user sees a friendly VS Code popup instead of a Java stack trace on stderr. Added five `cliArgs.test.ts` cases (defaults pass; rejects 0, < 8, NaN, non-integer).

### Cross-cutting / build / tests

#### 75. **[INC]** `vscode-extension` is not in the Gradle build graph
`settings.gradle.kts:21-27` only includes `:core`, `:core-bundled`, `:spotless`, `:cli`, `:intellij-plugin`. The `modules/vscode-extension/` tree is npm-managed but the README mentions all five integrations as a single product. CI verification for `npm test` is presumably wired separately. Document the CI/build graph (architecture.md does not mention vscode-extension's outside-graph status).

#### 76. **[QUAL]** `tasks.assemble { dependsOn("generateDocs") }` couples assembly to Python and mkdocs
`build.gradle.kts:146-148`. A user without Python (or without internet for `pipInstall`) cannot run `./gradlew assemble`. The contributor guide should call this out, or the dependency should move to a separate `release` task.

#### 77. **[QUAL]** `notCompatibleWithConfigurationCache("eval harness uses environment variables")`
`modules/core/build.gradle.kts:44`. Modern Gradle (8.x+) supports environment variables in the configuration cache via `providers.environmentVariable(...)`. Convert the eval task and remove the carve-out.

#### 78. **[QUAL]** `dependencyAnalysis { issues { all { onAny { severity("fail") } } } }`
`build.gradle.kts:76-84` is in the *root* build but the plugin is not declared in this file's `plugins {}` block. Either it's auto-applied by `com.autonomousapps.build-health` (settings.gradle.kts:10) or the configuration block fails silently. Verify and document.

#### 79. **[DONE]** `BundledArtifactTest.shadedJar_doesNotExposeOriginalThirdPartyPackageRoots` is incomplete
`BundledArtifactTest.java:55-76` checks `com/github/javaparser/`, `org/slf4j/`, `org/jspecify/`. But `org.checkerframework`, `kotlin/`, and any future runtime dep (e.g. if `picocli` ever leaks in) would slip through. Either enumerate the full no-go list or use the inverse: every class entry must be under `io/princeofspace/` (allowing `META-INF/`).

Resolution: replaced with `shadedJar_onlyContainsProjectAndMetaInfEntries` which inverts the check — every non-directory zip entry must start with `io/princeofspace/` (project + relocated `io/princeofspace/shaded/...`) or `META-INF/`. Any future un-relocated dependency (kotlin, checkerframework, picocli, etc.) now fails the test instead of slipping through the original three-prefix denylist.

#### 80. **[QUAL]** Test discoverability: `eval`, `showroom-golden`, default
`modules/core/build.gradle.kts:33-78` defines three test entry points with subtle filtering: `tasks.test` excludes `eval`, `evalTest` includes only `eval`, `showroomGoldenTest` includes only `showroom-golden`. The default `tasks.test` will *also* run `showroom-golden` tagged tests — was that intentional? If so, document; if not, add `excludeTags("showroom-golden")` to the default.

#### 81. **[INC]** `core` and `spotless` get sources/javadoc, but `core-bundled` also matches "core"
`build.gradle.kts:39-43`: `if (project.name.contains("core") || project.name.contains("spotless"))`. `core-bundled` matches "core" — works by accident, not intent. Use exact equality (`project.name in setOf("core", "spotless", "core-bundled")`).

#### 82. **[QUAL]** `prince.maxConvergencePasses` system property is not a public knob
`FormattingEngine.java:99-109` reads a JVM system property to control convergence retries, but this is documented nowhere outside the source. Either expose via `FormatterConfig` (typed and validated) or document under "Diagnostics" in the user docs.

### Test infrastructure & dependencies

#### 83. **[BUG]** `IdempotencyFuzzTest` is fully deterministic — no actual fuzzing
`IdempotencyFuzzTest.java:52` seeds `SplittableRandom rng = new SplittableRandom(0xFEEDBEEFL)`. Every CI run executes the *same* 200 (config, snippet) pairs. Real-world idempotency drifts are caught by goldens; this "fuzz" test cannot find new bugs that the seed didn't already exercise. Either:
- Seed from `System.nanoTime()` and log the seed on failure for reproduction,
- Or accept it's a regression suite and rename to `IdempotencyRegressionMatrixTest`.

#### 84. **[BUG]** `IdempotencyFuzzTest` SNIPPETS array is tiny (6 entries)
`IdempotencyFuzzTest.java:21-40`. With 200 iterations and 6 snippets, each snippet is exercised ~33 times — most variance comes from configs, not source shapes. Add more snippets (lambdas, switch expressions, text blocks, generics, annotations) or generate via `jqwik` (already a dependency).

#### 85. **[QUAL]** `InternalArchitectureTest.publicMethodsInInternalPackageAreAllowlisted` allowlist is brittle
`InternalArchitectureTest.java:46-60` lists 5 class names by string. A rename will silently disable the check. Use `@SuppressWarnings("internal-public")` annotation + ArchUnit predicate, or a `List.of(...)` constant referenced symbolically. Also: re-check whether any of those 5 classes still *needs* public methods after the recent refactors.

#### 86. **[QUAL]** Checkstyle is configured only for naming/structure
`config/checkstyle/checkstyle.xml:14`: comment says "avoid formatting rules that duplicate Spotless." Reasonable, but `MagicNumber` (line 23) ignores `-1,0,1,2` while the codebase uses larger constants (`12`, `4`, `7`, `8`, `11`) without `static final`. Either tighten `MagicNumber` (it would have caught finding #32) or accept literal use.

#### 87. **[QUAL]** Error Prone pinned to 2.45 due to NullAway compatibility
`gradle/libs.versions.toml:6`: pinned with a comment. This blocks newer Error Prone checks (e.g. `BadInstanceof`, `IdentityHashMapBoxing` ergonomics). Track NullAway 0.14+ and re-evaluate periodically; consider opening an upstream issue if NullAway is the only blocker.

#### 88. **[QUAL]** `slf4j-simple` dependency in versions catalog is unused
`gradle/libs.versions.toml:38` lists `slf4j-simple` but no module references it (only `slf4j-api`). Either wire it up as a `testRuntimeOnly` for visibility into engine logs during testing, or remove the catalog entry.

#### 89. **[BUG]** `bisect-eval-idempotency.sh` blindly uses `--no-configuration-cache`
`scripts/bisect-eval-idempotency.sh:16, 26`. With finding #77, removing the carve-out would let bisect run faster. Even today, the `compileJava` step (line 16) doesn't need the flag — only `evalTest` does.

#### 90. **[BUG]** `bisect-eval-idempotency.sh` exit-code semantics are ambiguous
`scripts/bisect-eval-idempotency.sh:6, 12-14, 18`. Returns 125 (skip) when `PRINCE_EVAL_ROOTS` is unset *and* when compile fails, but the docstring (line 6) says "125 = skip (does not compile)". Bisect-skip-on-missing-env is reasonable, but should be explicit in the docstring; bisect users may be surprised when a missing env var gets a "compile failed" diagnosis.

#### 91. **[INC]** `--no-configuration-cache` and `--rerun-tasks` together is redundant
`scripts/bisect-eval-idempotency.sh:26`: `--no-configuration-cache --rerun-tasks` — the latter implies cache miss for the test task. One of these is doing nothing per invocation.

### Documentation gaps

#### 92. **[DOC]** No documented matrix of `WrapStyle × closingParenOnNewLine × trailingCommas`
The README and architecture.md describe each knob individually, but the actual interaction matrix (8 combinations × 4 Java levels) lives only in goldens at `examples/outputs/<level>/<config>.java`. The `compare.html` viewer shows them but isn't part of `docs/`. Link from `docs/formatting-rules.md` to the comparison viewer or generate a static matrix table at docs build time.

#### 93. **[DOC]** `docs/canonical-formatting-rules.md` is referenced 3+ places but no rule index
Several findings above (#15, #33, #37) reference Rule 5/7/9. Consumers of the suggestions doc won't know which rule is which. Add an anchor index at the top of `canonical-formatting-rules.md` so that "R5" is link-targetable.

#### 94. **[DOC]** Settings storage scope (`WORKSPACE_FILE` vs `PROJECT_FILE$`) is not documented
Per finding #67, IntelliJ users may be surprised that `.idea/workspace.xml` is gitignored by default. Document the choice in `docs/architecture.md` or in the IntelliJ plugin README.

---

## Summary

94 findings across 8 sections. Suggested triage order:

1. **High-impact bugs (silent data loss / incorrect output):** #14 (SwitchEntry duplication), #35–37 (record/enum implements), #40 (block-comment blank-line drop), #45 (array initializer comments), #62 (Spotless per-file Formatter allocation), #66 (settings race), #71/72 (VS Code edit race), #79 (shaded-jar test gap).
2. **Off-by-one / miscalibration:** #25 (ObjectCreationExpr type-arg width), #31 (lambda toString width), #32 (keyword widths), #74 (java-version validation). (Legacy Java 1–7 surface — finding #10 — to be removed entirely under "minimum-supported = 8".)
3. **Concurrency / lifecycle:** #7 (per-pass JavaParser), #55 (CLI thread sharing), #65 (save-on-format reentrance).
4. **API & doc cleanup:** #1–6 (public API consistency), #4 (defaults dedup), #11 (AnnotationArranger), #38 (Java 8 carve-out), #93 (rule index).
5. **Refactor opportunities (high-leverage):** #21, #27, #34, #39 (duplicated comma-list / type-clause / clause / params code), #20 (VariableDeclarator helper extraction).
