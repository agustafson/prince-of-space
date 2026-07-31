# Technical Decision Register (TDR)

This register records important architectural and product decisions for Prince of Space.
Use it as the primary source of *why* design choices exist.

## How to use this document

- Read this file first when making non-trivial changes.
- Add new entries as append-only records (do not rewrite history).
- If a decision is superseded, mark it as superseded and link the replacement entry.

## Decision entries

### TDR-001: Small, curated configuration surface
- **Date:** 2026-04
- **Status:** Superseded by TDR-014
- **Decision:** Keep a bounded set of public formatter knobs (now 7 options), not zero-config and not highly granular.
- **Rationale:** Java teams need some style flexibility, but too many options cause bikeshedding and inconsistent output.
- **Consequences:** `FormatterConfig` remains intentionally small; feature requests for new options require strong justification.
- **Related docs:** `docs/formatting-rules.md`, `docs/architecture.md`

### TDR-002: JavaParser-based formatting pipeline
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Use JavaParser AST + custom pretty-printing for formatting.
- **Rationale:** Good API ergonomics, practical language coverage, and comment-aware workflow for formatter development velocity.
- **Consequences:** Language-level handling depends on JavaParser support; parser upgrades are part of maintenance.
- **Related docs:** `docs/architecture.md`, TDR-016

### TDR-003: Separation of public API and internal implementation
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Keep public API minimal (`io.princeofspace`, `io.princeofspace.model`); implementation belongs in `io.princeofspace.internal`.
- **Rationale:** Preserves API stability while allowing internal refactoring.
- **Consequences:** New public classes are rare; `Formatter` delegates to internal engine classes.
- **Related docs:** `docs/architecture.md`

### TDR-004: Single line length threshold
- **Date:** 2026-04
- **Status:** Accepted (revised)
- **Decision:** Use a single `lineLength` threshold instead of dual `preferredLineLength` + `maxLineLength`.
- **Rationale:** The dual-threshold model added complexity without meaningful benefit — the gap between preferred and max was rarely useful and made the API harder to understand. A single threshold is simpler, matches Prettier's `printWidth` model, and produces equivalent output.
- **Consequences:** Single wrapping threshold; simpler config surface.
- **Related docs:** `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

### TDR-005: Wrap styles are strategy-level, not per-construct settings
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Expose `WIDE`, `BALANCED`, `NARROW` wrap styles globally, rather than many per-node options.
- **Rationale:** Keeps configuration understandable and predictable.
- **Consequences:** Some edge cases are solved in formatter heuristics, not by adding bespoke knobs.
- **Related docs:** `docs/formatting-rules.md`

### TDR-006: Idempotency is a hard invariant
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Treat `format(format(x)) == format(x)` as mandatory behavior.
- **Rationale:** Non-idempotent formatters are unstable in CI and editor workflows.
- **Consequences:** Every new formatter behavior requires idempotency tests.
- **Related docs:** `docs/architecture.md`, `modules/core/src/test/java/io/princeofspace`

### TDR-007: Module split includes both normal and bundled core artifacts
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Publish both `core` (normal deps) and `core-bundled` (shaded) artifacts.
- **Rationale:** Supports both regular build integrations and classloader-sensitive environments.
- **Consequences:** Behavior parity between artifacts is tested and documented.
- **Related docs:** `docs/architecture.md`

### TDR-008: Integrations are first-class (CLI, Spotless, IntelliJ, VS Code)
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Treat integrations as product features, not side projects.
- **Rationale:** Formatter adoption depends on integration quality as much as formatting quality.
- **Consequences:** Integration modules are maintained with tests/docs and kept aligned with core behavior.
- **Related docs:** `README.md`, `modules/intellij-plugin/README.md`, `modules/vscode-extension/README.md`

### TDR-009: Real-world eval harness for Guava and Spring
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Use evaluation runs on large external codebases (Guava and Spring) as regression quality gates.
- **Rationale:** Synthetic tests alone miss important style and stability edge cases.
- **Consequences:** Eval reports are tracked under `docs/eval-results/`; parse errors and idempotency failures must remain zero.
- **Related docs:** `docs/evaluation.md`, `docs/eval-results/`

### TDR-010: Documentation structure shifts from plans to decisions
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Prefer decision records + architecture docs over active “implementation plan” narrative docs.
- **Rationale:** The project is beyond early scaffolding; historical plans are useful context but no longer primary guidance.
- **Consequences:** Keep research/priorities historical context, and remove stale implementation-plan/roadmap checklists from active docs.
- **Related docs:** TDR-016

### TDR-011: WrapStyle behavior for string concatenation is construct-uniform
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Treat `+` string concatenation wrapping the same as other list-like constructs for `WrapStyle` policy.
- **Rationale:** `BALANCED` should mean fit-or-tall consistently; allowing greedy packing only for string concatenation made behavior surprising and undermined predictability.
- **Consequences:** `BALANCED` and `NARROW` now put each `+` operand on its own continuation line when wrapping; `WIDE` retains greedy packing.
- **Related docs:** `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

### TDR-012: continuationIndentSize is additive
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Interpret `continuationIndentSize` as an indent delta added on top of the active enclosing indent, not as an absolute column from statement start.
- **Rationale:** Additive continuation indent yields consistent visual depth across nested contexts and avoids surprising left shifts for wrapped chains inside expressions.
- **Consequences:** Wrapped segments in nested expressions use the same continuation math as top-level wrapped segments; docs and tests should assert additive behavior.
- **Related docs:** `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

### TDR-014: Remove continuationIndentSize config, hardcode to 2 × indentSize
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Remove `continuationIndentSize` as a public configuration knob. Continuation indent is now always `2 * indentSize`, following the Oracle/IntelliJ convention.
- **Rationale:** When `continuationIndentSize == indentSize` (the previous default), wrapped method parameters and the method body are indented to the same column, making them visually indistinguishable. The `2×` convention eliminates this ambiguity by construction. No well-known opinionated Java formatter (google-java-format, Prettier, Black, ktlint) exposes continuation indent as a config knob. Reducing from 8 to 7 options simplifies the configuration surface and halves the showroom golden matrix (48→24 files).
- **Consequences:** The `FormatterConfig` record no longer has a `continuationIndentSize` record component; a derived method `continuationIndentSize()` returns `2 * indentSize`. Showroom goldens drop the `cont4`/`cont8` filename axis. IntelliJ plugin settings UI no longer shows a continuation indent spinner. TDR-012 (additive continuation indent) still applies — the indent is additive, just no longer user-configurable.
- **Related docs:** `docs/formatting-rules.md`, `docs/canonical-formatting-rules.md`, `docs/architecture.md`

### TDR-013: Showroom rule-uniformity migration is complete
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** The showroom rule-uniformity work is complete: `wrapStyle` behavior is consistent across the showroom’s list-like and wrapping constructs, with regression coverage in `WrappingFormattingTest` and an overview check in `RuleUniformityTest`. (Earlier stepwise tasks spanned `WidthMeasurer` introduction, `BALANCED` string concat alignment with TDR-011, shared comma-list wrapping for enum/array/type parameters, `extends` clause wrapping, `closingParenOnNewLine` unification, try-with-resources/`for`/`switch` wrapping, and `BlankLineNormalizer` alignment.)
- **Rationale:** One wrap vocabulary (`wide` / `balanced` / `narrow`) keeps configuration predictable; the migration aligned docs, the Java printer, and golden outputs.
- **Consequences:** Further wrapping tweaks should update `docs/formatting-rules.md` and the showroom in lockstep; avoid reintroducing per-construct ad-hoc wrap semantics without a TDR.
- **Related docs:** `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/RuleUniformityTest.java`

### TDR-015: Wrapped method chains use indentSize, not 2 × indentSize
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** When a method chain wraps and each `.method(...)` segment goes on its own continuation line, indent each segment by exactly **one `indentSize`** step beyond the receiver's line — not the `2 * indentSize` continuation indent used for delimited list continuations (Rule 3 / TDR-014).
- **Rationale:** The `2 * indentSize` continuation indent exists to make wrapped parameters visually distinct from the method body inside (e.g. `void foo(\n        String x) {\n    body();`). Method chains do not need that disambiguation: every segment already begins with a leading `.`, which is its own visual delimiter, and the receiver itself sits at the enclosing block's indent. With the old `2 * indentSize` rule, deeply nested chains (a stream inside a `.map(...)` inside another stream) drifted far to the right and visually compounded the depth of plain Java code. Reducing the chain step to a single indent unit keeps wrapped chains readable while leaving non-chain continuations (parameter lists, binary expressions, ternaries, etc.) at the well-established `2 * indentSize` depth.
- **Consequences:**
  - `MethodChainFormatter` emits chain continuations via a new `LayoutContext.printChainIndent()` helper that prints exactly one indent step.
  - When a wrapped method chain appears as an operand of a wrapped binary chain (Rule 6), `BinaryExprFormatter` pushes one extra `indentSize` so chain segments remain visually distinct from the operator line that introduces them. Without this, segments would be flush with the operator continuation column and the operator/operand separation would be ambiguous.
  - All 24 showroom golden files were regenerated; existing chain assertions in `WrappingFormattingTest` were updated to reflect the new column math (chain at base + indentSize, lambda body inside a chain segment at chain + indentSize, text-block-receiver chain at base + indentSize).
  - TDR-012 (additive continuation indent) and TDR-014 (continuation indent is fixed at `2 * indentSize`) still apply to every other wrapping construct; this TDR is a Rule 7 carve-out only.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rules 3, 7), `docs/formatting-rules.md` (Part 1 §3, Part 3 "Method Chaining"), `modules/core/src/main/java/io/princeofspace/internal/MethodChainFormatter.java`, `modules/core/src/main/java/io/princeofspace/internal/LayoutContext.java`, `modules/core/src/main/java/io/princeofspace/internal/BinaryExprFormatter.java`

### TDR-016: Mission, ecosystem context, and research bibliography
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Retain the following as durable context (consolidated from former `docs/project-priorities.md` and `docs/research-notes.md` when those historical docs were retired).
- **Mission:** Build a Java formatter that is readable, meaningfully configurable (small public surface: 7 options; see TDR-001, TDR-014), and straightforward to wire into real projects (see TDR-007, TDR-008).
- **Ecosystem — pain points in other Java formatters (informal):**

| Tool | Usual pain points (not exhaustive) |
|------|------------------------------------|
| **google-java-format** | Effectively unconfigurable; 2-space default (non-Android); heavy rightward indent / lambdas often criticized |
| **palantir-java-format** | Very limited configurability; still a GJF-style fork in spirit |
| **Eclipse JDT** | Opaque XML; painful to use without the Eclipse config workflow |
| **IntelliJ** | No stable standalone CLI; hundreds of options encourage drift |
| **Prettier (Java)** | Node runtime; teams care about version churn vs JVM-native stacks |
| **Spring Java Format** | Fixed style, Eclipse-centric integration patterns |

*Commentary is opinionated; teams differ. The point of the table is the product gap PoS is aimed at: Prettier/ktlint-like *bounded* config plus good JVM/CI/IDE story.*

- **Configuration sweet spot (research):** Ecosystems show `gofmt`-style 0 options work where the culture is uniform; `black`-style “few” options (line length, indents) cover most real disagreements; very large option sets (e.g. rustfmt-scale) add fatigue. Prince of Space targets a **small curated** surface (7 options) — see TDR-001.
- **What Java teams often rank highly when choosing formatters (informal):** indent width; line length; lambda layout; method-chain layout; continuation indent; import policy (here delegated to Spotless, README non-goals); wrapping policy; blank-line policy.
- **Parser choice (extends TDR-002):** **JavaParser** was chosen for a public, comment-friendly AST, practical API/visitor model, and formatting-friendly workflows. *Alternatives considered:* **Eclipse JDT** — heavier, more IDE-coupled. **javac internal tree** (as used by some formatters) — strong language parity but comment handling and API stability are awkward for a new formatter. **Spoon** — JDT-based; more transformation-oriented than we need. The canonical “use JavaParser” decision remains TDR-002; this entry preserves *why* alternatives were less attractive.
- **Spotless:** First-party `PrinceOfSpaceStep` and Spotless as the build-tool integration path are product decisions in TDR-008. Early research also noted Spotless’s `FormatterStep` model and `custom` / classpath integration patterns; see `docs/evaluation.md` for the harness.
- **Bibliography (external background):** [Why are there no decent code formatters for Java?](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/) (Jan Ouwens); [Prettier option philosophy](https://prettier.io/docs/option-philosophy); [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html); [rustfmt configuration](https://github.com/rust-lang/rustfmt/blob/main/Configurations.md); [Black](https://github.com/psf/black); [Spotless](https://github.com/diffplug/spotless); [Oracle Java code conventions (indentation)](https://www.oracle.com/java/technologies/javase/codeconventions-indentation.html).
- **Consequences:** Product positioning and ecosystem comparisons live here; normative *formatter behavior* remains `docs/canonical-formatting-rules.md`. Historical priority-stack items (P0–P3) are subsumed by shipped modules and the decision register; treat them as background, not a roadmap checklist.
- **Related docs:** TDR-002, TDR-007, TDR-008, `README.md`, `docs/evaluation.md`

### TDR-017: Nested wrapped `(...)` lists and type-body comment spacing
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** (1) For wrapped comma-separated **argument** lists only, push extra `SourcePrinter` indent levels while printing list items so nested calls stack continuation indent correctly; suppress redundant explicit `printCont()` in that scope so binary/operator continuations inside an argument do not double-count against the printer prefix. Do **not** activate that scope for a **single** wrapped call expression (e.g. `new X("""...""".formatted(...))`) so method-chain segments on the same argument keep Rule 7 column math. Wrapped `<T, U>` type-parameter breaks inside an argument still emit an explicit continuation via `LayoutContext.printRawContinuation()`. Formal parameter lists continue to use the same indent push whenever parameters wrap. (2) Emit blank lines **between** type members instead of an unconditional leading newline before every member, so consecutive line comments before the first member are not separated by a manufactured blank line.
- **Rationale:** Continuation was previously applied as a flat `2 * indentSize` print on every wrapped line regardless of nesting, so inner `)` delimiters aligned with outer ones and looked “stacked” at the wrong column; `printMembers`’ leading newline interacted badly with orphan comment draining before the first field.
- **Consequences:** `PrincePrettyPrinterVisitor.printArguments`, `ArgumentListFormatter`, `LayoutContext`, `DeclarationFormatter`, and `printMembers`; showroom goldens and wrapping/comment tests updated.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rules 3, 8, 9, 10), `WrappingFormattingTest`, `CommentPreservationTest`

### TDR-018: Conventional Commits for showroom vs. Nyx patch/minor
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Document explicit guidance: prefer **`feat:`** (and **`feat!:`** / **`BREAKING CHANGE:`** when appropriate) for showroom and golden updates that reflect **new or changed formatting behavior** or **new showcase coverage**; reserve **`fix:`** for **bugfixes** (incorrect output relative to the intended rules). This aligns changelog sections (“Added” vs “Fixed”) and version bumps (minor vs patch) with user-visible meaning. Nyx’s **highest-bump-wins** rule is unchanged: more **patch** releases in practice require release lines where only patch-level types appear, or separate releases.
- **Revision (same entry):** (1) **New numbered showroom scenario** (or large showcase expansion) **usually** → **`feat:`** — signals a **broader, intentional** change (example `7e619f8`), not a one-line hotfix. (2) **Substantive** edits to `docs/canonical-formatting-rules.md` (redefined rules, public knob removal, contract change) **usually** → **`feat!:`** with a footer when required — e.g. `bd21397` (TDR-014), `846fa82` (TDR-015). (3) **Small** canonical amendments that mainly document a **bugfix** (wrong output or a violated invariant) can stay **`fix:`** or use **`feat:`** — e.g. `db0658c`. This does **not** mean “every output change is major” (TDR-018 discussion).
- **Rationale:** Showroom diffs are often categorized as `fix` by habit, which understates product impact and mis-files notes under “Fixed” when the work is a feature or contract change. Calling out **breaking** golden churn explicitly helps integrators.
- **Consequences:** Maintainers and contributors follow `docs/contributing.md`; no `.nyx.yml` change required.
- **Related docs:** `docs/contributing.md`, `RELEASING.md`, `docs/showroom-scenarios.md`

### TDR-019: Nested wrapped-call closer alignment and single-arg wrap policy
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Refine nested wrapped `(...)` behavior in three parts: (1) for co-line nested wrapped call openers, `closingParenOnNewLine=true` may compact closer runs on one closer line (`));`, `)));`) instead of emitting one `)` line per nesting level; line-separated nested openers keep separate aligned closer lines. (2) Wrapped delimited-list scope indentation is continuation-line aware: when a list opens on an already-continued line, list continuation lines are based on that effective continuation start column plus Rule 3's `2 * indentSize`. (3) The "single wrapped argument stays inline" carve-out is narrowed to scoped method-chain receivers only; other wrapped single arguments break before the argument body.
- **Rationale:** Repeated user reports showed stacked closer columns and under-indented nested argument lists in ternary/binary continuation contexts. Earlier TDR-017 scope handling improved nested list indentation but left closer placement and single-arg wrapping edge cases unresolved.
- **Consequences:** `PrincePrettyPrinterVisitor`, `LayoutContext`, and `ArgumentListFormatter` now coordinate co-line closer compaction, continuation-aware wrapped-list scope entry, and single-arg break-before behavior. New regression coverage lives in `ClosingParenAlignmentTest`; showroom goldens were regenerated to reflect updated nested-call output.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rules 3, 8), `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/internal/ClosingParenAlignmentTest.java`

### TDR-020: Default convergence pass budget
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Raise the engine's default `maxConvergencePasses` (additional single-format attempts after the first) to **11**, so the hard idempotency guarantee can be met in one `format()` call for WIDE mode at a short line length on large corpora (for example Spring Framework eval inputs). Override remains **`prince.maxConvergencePasses`** (non-negative integer).
- **Rationale:** The prior default (**3** extra passes ⇒ **4** attempts total) was sufficient for typical inputs and comment re-attachment, but real sources showed **monotonic** refinement across many passes—greedy comma/call wrapping moving breakpoints until stable—not oscillation. Hitting `NonConvergent` there was a budget failure, not proof of non‑existence of a fixed point.
- **Consequences:** Worst-case formatting work scales with the budget only when outputs keep changing; stable outputs still exit on the first equality check. Regression fixtures live in `WideSpringCorpusConvergenceRegressionTest` under test resources.
- **Related docs:** `FormattingEngine`, `RealWorldEvalTest`, `RELEASING.md`

### TDR-021: Trailing-lambda layout keeps the lambda header on the call line
- **Date:** 2026-04-28 (extended 2026-04-29)
- **Status:** Accepted
- **Decision:** When the **last argument** of a wrapped method/constructor call is a **lambda — block- or expression-bodied** — keep any leading arguments and the lambda header (`() -> {`, `(a, b) -> {`, `s ->`, `value ->`, etc.) on the call line, let the lambda body wrap according to its own rules (block body via its block indent; expression body via the receiver chain or other inner wrap mechanic), and place the closing `)` immediately after the lambda body (`});`, `))`, `.lastSegment())`, etc.) at the call's indent column — regardless of `closingParenOnNewLine`. This holds even when the resulting opener line slightly exceeds `lineLength`. The rule covers single-argument calls and multi-argument calls alike; the only fallback is when a *leading* argument itself carries a leading line/block comment, or another leading argument is itself a block lambda (multi-block-lambda calls remain ambiguous and use the regular per-arg break path). Single-parameter unparenthesized lambda parameters (e.g. `s`, `value`) never themselves wrap because there is no syntactic break point — only multi-parameter parenthesized lambda parameter lists may wrap.
- **Rationale:** Other mainstream formatters — palantir-java-format, Prettier, ktlint, and Google's style for Kotlin trailing-lambda — uniformly treat a trailing lambda as the "expanded" argument and keep its header inline so the body reads as a natural block or chain. The previous "one arg per line" wrap path placed `() ->` / `s ->` on its own continuation line, which breaks the visual coupling between the call and the lambda body and reads worse on any non-trivial line. Soft overflow on the opener line is the standard tradeoff in those formatters because the alternative is uglier per-arg breakage. The extension to expression-bodied and single-arg lambdas was driven by feedback that `.map(\n        s -> s\n            .toLowerCase()\n            ...\n)` reads strictly worse than `.map(s -> s\n        .toLowerCase()\n        ...)` for stream-like chains.
- **Consequences:** Wrapped calls with a trailing lambda — single- or multi-arg, block- or expression-bodied — now render in palantir-style trailing-lambda layout, with the closing `)` always inline with the lambda body and `closingParenOnNewLine` overridden in those cases. Idempotency holds because the layout is a fixed point of the trailing-lambda branch. The `printArguments` trailing-lambda branch also clears any stale `continuationLineStartColumn` so an inner wrapped call inside the lambda body anchors its indent to the surrounding block, not to a leftover continuation column from an earlier statement. Coverage: `WrappingFormattingTest.trailingBlockLambda_*` and `WrappingFormattingTest.trailingLambda_*`.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rule 8), `modules/core/src/main/java/io/princeofspace/internal/ArgumentListFormatter.java`, `modules/core/src/main/java/io/princeofspace/internal/PrincePrettyPrinterVisitor.java`

### TDR-022: Wrapped lambda parameter list closer aligns to the opener column
- **Date:** 2026-04-29
- **Status:** Accepted
- **Decision:** When a parenthesized lambda's formal parameter list wraps, the closing `) ->` line aligns to the opener `(` column — i.e. the same indentation as the line containing `(` — rather than one `indentSize` step past it. This brings the closer placement into agreement with Rule 8's general statement ("closing delimiter is on its own line at the opener's indentation column") and matches the constructor/method/`try`-resource wrap shape.
- **Rationale:** The earlier `openParen + indentSize` placement was a deliberate visual carve-out intended to make the `) ->` arrow stand out from the parameter lines, but it left the closer dangling halfway between the opener column and the parameter column, breaking the visual rhyme readers rely on for every other own-line `)` in the formatter. Aligning to the opener column matches the canonical Rule 8 text, removes the special case from the lambda path, and produces a layout that reads as a normal wrapped delimited list with the arrow trailing the closer (still visually distinct because of the `->` itself).
- **Consequences:** Showroom scenario 44 (`longLambdaParameters`) shifts the `) -> ...` line by `indentSize` to the left in every level/wrap-style/closer combination (24 golden files updated). The `printLambdaParameters` block-indent step is dropped (`padToColumn(openParenStartColumn)` instead of `+ indentSize`). `ContinuationIndentStepPropertyTest` was retargeted to assert the new alignment; `WrappingFormattingTest.lambdaParameterList_insideWrappedChainCall_*` updated likewise. Idempotency holds since closer placement is a deterministic function of the opener column. Coverage: `ContinuationIndentStepPropertyTest`, `WrappingFormattingTest.lambdaParameterList_insideWrappedChainCall_alignsParametersAndCloseParen`, `FormatterShowcaseGoldenTest`.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rule 8), `modules/core/src/main/java/io/princeofspace/internal/PrincePrettyPrinterVisitor.java`, `examples/outputs/**`

### TDR-023: Enum constant lists are never collapsed
- **Date:** 2026-04-29
- **Status:** Accepted
- **Decision:** Regardless of `wrapStyle` or `lineLength`, **never** collapse `enum` constant declarations onto fewer lines—no greedy horizontal packing (`WIDE`-style grouping of multiple constants per line), and no single-brace `{ A, B, C }` form when there is at least one constant. Every constant occupies its own line after `{` (`DeclarationFormatter`): same shape as readable source and typical style-guide expectations (`enum`-specific exception to Rule 5’s generic list semantics—see **`docs/canonical-formatting-rules.md`**).
- **Rationale:** Enums behave like small tables of identifiers; cramming constants onto fewer lines hides structure and defeats diff-friendly editing. Packing also interacted badly with comment re-attachment widths in greedy mode.
- **Consequences:** `DeclarationFormatter#printEnumConstants` ignores `WrapStyle`; the prior one-line shortcut when the enum had only constants under the line budget is removed. Tests and showroom goldens (`FormatterShowcase` enum sections) reflect the stacked layout everywhere.
- **Related docs:** `docs/canonical-formatting-rules.md` (Rule 5), `docs/formatting-rules.md` (Enum constants), `modules/core/src/main/java/io/princeofspace/internal/DeclarationFormatter.java`

### TDR-024: CLI exit code 3 for non-convergent format; remove no-op `AnnotationArranger` pass
- **Date:** 2026-05-01
- **Status:** Accepted
- **Decision:** (1) The CLI uses **exit code 3** when the engine returns `FormatResult.NonConvergent` (including when path-scoped), distinct from **2** for parse/config/IO and other failures, so automation can flag likely formatter defects. (2) The empty `AnnotationArranger` `ModifierVisitor` is **removed** from the transform pipeline; annotation layout remains the pretty printer’s responsibility. (3) `FormatterException` can be constructed with a `FormatResult.Failure` and exposes `isNonConvergent()` / `formatFailure()` for throwing API users.
- **Rationale:** Non-convergence is a different failure class from user/syntax errors. The no-op visitor added a transform pass and documentation burden with no behavior. Typed `FormatterException` preserves sealed `FormatResult` semantics for tools that still use `format(String)`.
- **Consequences:** `io.princeofspace.cli.Main` documents exit codes; batch and stdin use `formatResult`. `AnnotationArrangerTest` renamed to `AnnotationLayoutFormattingTest` (end-to-end). `docs/architecture.md` pipeline and `CHANGELOG.md` updated.
- **Related docs:** `docs/architecture.md`, `modules/cli/src/main/java/io/princeofspace/cli/Main.java`, `modules/core/src/main/java/io/princeofspace/FormatterException.java`, `modules/core/src/main/java/io/princeofspace/internal/FormattingEngine.java`

### TDR-025: Maven Central `groupId` uses a project-scoped GitHub namespace
- **Date:** 2026-05-02
- **Status:** Accepted
- **Decision:** Publish JVM artifacts under **`io.github.agustafson.princeofspace`** so Maven coordinates disambiguate this project from other libraries under the same GitHub user. The first release (**0.1.0**) remains at the legacy group **`io.github.agustafson`** on Central; **new releases** use the project-scoped group. Register **`io.github.agustafson.princeofspace`** with Sonatype Central Portal (namespace verification) before the first publish under it. Java **package** names stay **`io.princeofspace.*`** — unchanged; only Maven `groupId` / Gradle `group` move.
- **Rationale:** A bare `io.github.<user>` group is fine for a single library but becomes ambiguous when multiple unrelated projects publish from the same account. A trailing segment (`princeofspace`, analogous to multi-segment groups such as Caffeine’s `com.github.ben-manes.caffeine`) keeps Central namespaces readable without coupling to Java source packages.
- **Consequences:** `gradle.properties` `group=…`; POMs and staging uploads use the new `groupId`. `README.md`, `RELEASING.md`, and `docs/architecture.md` document the coordinates; integrators pin **`io.github.agustafson:…:0.1.0`** only for that build.
- **Related docs:** `README.md`, `RELEASING.md`, `docs/architecture.md`, TDR-007

### TDR-026: Benchmark-only fast single-pass `Formatter` overload + `BenchDiagnostics`
- **Date:** 2026-05-03
- **Status:** Accepted
- **Decision:** (1) Add an overload `Formatter(FormatterConfig, boolean fastSinglePass)` with default `false`. When `fastSinglePass` is `true`, the engine returns after one parse/transform/print pass (no fixed-point convergence loop), for throughput comparison against single-invocation JVM formatters in `:formatter-benchmark`. Default construction remains strict fixed-point formatting. (2) Add `io.princeofspace.BenchDiagnostics` controlled by `-Dprince.bench.diagnostics=true`, aggregating per-phase timings and pass-count histograms for optional Markdown sections in benchmark reports.
- **Rationale:** Product idempotency (Rule 1) stays tied to strict convergence in normal use; benchmarks need an explicit, comparable “single format call” cost without implying the library skips convergence by default. Diagnostics must be reachable from the benchmark module without exposing internal packages.
- **Consequences:** Corpus eval (`:core:evalTest`) and default `Formatter()` behavior unchanged. README/Spring throughput tables can list both strict and fast Prince rows. Integrators should not use fast mode unless they understand it does not prove a formatting fixed point.
- **Related docs:** `docs/benchmarks.md`, `modules/formatter-benchmark`, `io.princeofspace.BenchDiagnostics`, `io.princeofspace.Formatter`

### TDR-027: README Maven coordinates track last Central release, not dev SNAPSHOT
- **Date:** 2026-05-03
- **Status:** Accepted
- **Decision:** Keep **`readmeMavenCoordinatesVersion=`** in `gradle.properties` as the version string **`syncReadmeVersions`** writes into `README.md`. After each successful publish, the release workflow sets it to **`RELEASE_VERSION`** while bumping **`version=`** to the next `-SNAPSHOT`.
- **Rationale:** Local development uses a SNAPSHOT `version=` line; showing that in Quick Start misleads copy-paste integrators. Separating “latest artifact on Central” from “next patch line under development” keeps README honest without coupling Nyx inference to docs.
- **Consequences:** Release housekeeping updates both properties; `.github/workflows/sync-readme-versions.yml` continues to trigger on `gradle.properties` edits.
- **Related docs:** `README.md`, `RELEASING.md`, `docs/benchmarks.md`, `build.gradle.kts` (`syncReadmeVersions`)

### TDR-028: Publish CLI shaded jar to Central; `nativeCmd` + `maven-dependency-plugin` is the documented Maven integration path
- **Date:** 2026-06-30
- **Status:** Partially superseded by TDR-029 (native Maven support upstream) — the CLI-jar-on-Central decision and the `nativeCmd` documentation remain valid as the fallback path for Spotless versions older than 3.9.0
- **Decision:** Publish `modules/cli`'s shaded jar to Maven Central as **`prince-of-space-cli`**, alongside `core`, `core-bundled`, and `spotless` (same `maven-publish` + `signing` + staging-deploy pattern as `core-bundled`, since both are shadowJar-based). Document Maven integration as: `maven-dependency-plugin:copy` to fetch `prince-of-space-cli` into `${project.build.directory}`, then Spotless's `nativeCmd` generic step to invoke `java -jar ... --stdin`. The CLI jar continues to also be attached to GitHub Releases (dual publication, same artifact).
- **Rationale:** `PrinceOfSpaceStep` (the Gradle `FormatterStep` factory in `:spotless`) has no Maven equivalent — Spotless's Maven plugin only supports custom steps via its built-in generic step types. Empirically verified that `jsr223` cannot serve as that path: its `<dependency>` element is a single Maven coordinate (`JarState.from(String, Provisioner)`), so it cannot simultaneously supply a JSR-223 script engine and the `prince-of-space-core`/`prince-of-space-bundled` classes — confirmed with both coordinates and with a comma-joined coordinate string (treated as one invalid coordinate, not a list). `nativeCmd` is the only Spotless-native escape hatch left, and its stdin/stdout contract matches the CLI's `--stdin` mode exactly. This mirrors how third-party formatters generally reach Maven users: either upstream into `diffplug/spotless` itself (google-java-format, palantir-java-format, prettier) or ship a fully standalone Maven plugin (e.g. `fmt-maven-plugin`) — neither of which this project takes on; `nativeCmd` is the pragmatic middle path. `nativeCmd`'s `pathToExe` was confirmed (empirically) to resolve as a literal filesystem path, not via `$PATH`, so the documented example uses `${java.home}/bin/java` (Maven property substitution, portable across JDK installs) rather than a bare `java` command.
- **Consequences:** A fourth Central artifact (`prince-of-space-cli`) ships with `-sources.jar`/`-javadoc.jar`/`.pom`/`.asc` like the others. `build.gradle.kts`'s `syncReadmeVersions` `mavenXmlCoord` regex generalized from a hardcoded `prince-of-space-core` artifactId match to `prince-of-space-[a-z-]+` so the new Maven XML coordinate block in `README.md` also auto-syncs on release. `.github/workflows/release.yml` builds/tests/signs `:cli` alongside the other three modules. No first-party Maven plugin is added; users still need the `maven-dependency-plugin` + `nativeCmd` boilerplate (documented in `README.md`) **unless** they're on Spotless Maven plugin 3.9.0+ — see TDR-029.
- **Related docs:** `README.md` ("Spotless (Maven)"), `RELEASING.md`, `docs/architecture.md`, `modules/cli/build.gradle.kts`, TDR-008, TDR-025, TDR-029

### TDR-029: Native `princeOfSpace()` support upstreamed into diffplug/spotless
- **Date:** 2026-07-31
- **Status:** Accepted
- **Decision:** Contributed a `PrinceOfSpaceStep` `FormatterStep` (using Spotless's `JarState`/reflection "glue" pattern against `prince-of-space-core`, not this project's own `:spotless` module) plus Gradle `princeOfSpace()` DSL and Maven `<princeOfSpace>` `FormatterStepFactory` directly to `diffplug/spotless` ([PR #2991](https://github.com/diffplug/spotless/pull/2991), merged 2026-07-27). Released in Spotless Gradle plugin **8.9.0**, Spotless Maven plugin **3.9.0**, and Spotless lib **4.9.0**. These are now the documented primary integration paths in `README.md` ("Spotless (Gradle)" / "Spotless (Maven)"), ahead of both this project's own `:spotless` module (TDR-008) and the `nativeCmd` workaround (TDR-028).
- **Rationale:** TDR-028 identified upstreaming into `diffplug/spotless` as the same path taken by google-java-format, palantir-java-format, and prettier-java, and noted it was not being pursued at the time. Doing so removes the Maven-specific gap entirely (no `nativeCmd`/`maven-dependency-plugin` boilerplate needed on current Spotless) and gives Gradle users a step that ships with Spotless itself rather than requiring an extra classpath dependency.
- **Consequences:** This project's own `:spotless` module (`io.princeofspace.spotless.PrinceOfSpaceStep`, `prince-of-space-spotless` artifact) is no longer the primary Gradle path but remains useful for: (a) Spotless Gradle plugin versions older than 8.9.0, and (b) callers who want to build a `FormatterConfig` object programmatically rather than through the Gradle DSL. The `nativeCmd` + `maven-dependency-plugin` workaround from TDR-028 remains documented as the fallback for Spotless Maven plugin versions older than 3.9.0. Upstream's `PrinceOfSpaceStep` and this project's are two independent implementations against the same `prince-of-space-core` public API — behavioral parity depends on both tracking the same `FormatterConfig` option set; no shared code exists between them today.
- **Related docs:** `README.md` ("Spotless (Gradle)", "Spotless (Maven)", "Non-goals"), TDR-008, TDR-028, `CHANGELOG.md`
