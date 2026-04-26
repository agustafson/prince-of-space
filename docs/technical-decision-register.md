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
- **Decision:** The showroom rule-uniformity work is complete: `wrapStyle` behavior is consistent across the showroom’s list-like and wrapping constructs, with regression coverage in `WrappingFormattingTest` and an overview check in `RuleUniformityTest`. (Earlier stepwise tasks spanned `WidthMeasurer` introduction, `BALANCED` string concat alignment with TDR-011, shared comma-list wrapping for enum/array/type parameters, `extends` clause wrapping, `closingParenOnNewLine` unification, try-with-resources/`for`/`switch` wrapping, and `AnnotationArranger` / `BlankLineNormalizer` alignment.)
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
