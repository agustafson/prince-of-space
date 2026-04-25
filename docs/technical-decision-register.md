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
- **Status:** Accepted
- **Decision:** Keep a bounded set of public formatter knobs (8 options), not zero-config and not highly granular.
- **Rationale:** Java teams need some style flexibility, but too many options cause bikeshedding and inconsistent output.
- **Consequences:** `FormatterConfig` remains intentionally small; feature requests for new options require strong justification.
- **Related docs:** `docs/formatting-rules.md`, `ARCHITECTURE.md`

### TDR-002: JavaParser-based formatting pipeline
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Use JavaParser AST + custom pretty-printing for formatting.
- **Rationale:** Good API ergonomics, practical language coverage, and comment-aware workflow for formatter development velocity.
- **Consequences:** Language-level handling depends on JavaParser support; parser upgrades are part of maintenance.
- **Related docs:** `ARCHITECTURE.md`, `docs/research-notes.md`

### TDR-003: Separation of public API and internal implementation
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Keep public API minimal (`io.princeofspace`, `io.princeofspace.model`); implementation belongs in `io.princeofspace.internal`.
- **Rationale:** Preserves API stability while allowing internal refactoring.
- **Consequences:** New public classes are rare; `Formatter` delegates to internal engine classes.
- **Related docs:** `ARCHITECTURE.md`

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
- **Related docs:** `ARCHITECTURE.md`, `modules/core/src/test/java/io/princeofspace`

### TDR-007: Module split includes both normal and bundled core artifacts
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Publish both `core` (normal deps) and `core-bundled` (shaded) artifacts.
- **Rationale:** Supports both regular build integrations and classloader-sensitive environments.
- **Consequences:** Behavior parity between artifacts is tested and documented.
- **Related docs:** `ARCHITECTURE.md`

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
- **Related docs:** `docs/research-notes.md`, `docs/project-priorities.md`

### TDR-011: WrapStyle behavior for string concatenation is construct-uniform
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Treat `+` string concatenation wrapping the same as other list-like constructs for `WrapStyle` policy.
- **Rationale:** `BALANCED` should mean fit-or-tall consistently; allowing greedy packing only for string concatenation made behavior surprising and undermined predictability.
- **Consequences:** `BALANCED` and `NARROW` now put each `+` operand on its own continuation line when wrapping; `WIDE` retains greedy packing.
- **Related docs:** `docs/rule-uniformity-migration-plan.md`, `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

### TDR-012: continuationIndentSize is additive
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Interpret `continuationIndentSize` as an indent delta added on top of the active enclosing indent, not as an absolute column from statement start.
- **Rationale:** Additive continuation indent yields consistent visual depth across nested contexts and avoids surprising left shifts for wrapped chains inside expressions.
- **Consequences:** Wrapped segments in nested expressions use the same continuation math as top-level wrapped segments; docs and tests should assert additive behavior.
- **Related docs:** `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/WrappingFormattingTest.java`

### TDR-013: Showroom rule-uniformity migration is complete
- **Date:** 2026-04
- **Status:** Accepted
- **Decision:** Treat `docs/rule-uniformity-migration-plan.md` Tasks 1.1 through 6.2 as done: `wrapStyle` behavior is consistent across the showroom’s list-like and wrapping constructs, with regression coverage in `WrappingFormattingTest` and an overview check in `RuleUniformityTest`.
- **Rationale:** One wrap vocabulary (`wide` / `balanced` / `narrow`) keeps configuration predictable; the rule-uniformity plan drove alignment between docs, the Java printer, and golden outputs.
- **Consequences:** Further wrapping tweaks should update `docs/formatting-rules.md` and the showroom in lockstep; avoid reintroducing per-construct ad-hoc wrap semantics without a TDR.
- **Related docs:** `docs/rule-uniformity-migration-plan.md`, `docs/formatting-rules.md`, `modules/core/src/test/java/io/princeofspace/RuleUniformityTest.java`
