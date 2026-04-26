# Canonical Formatting Rules (Normative)

This document is the canonical, normative rule set for Prince of Space formatter behavior.

- If this document conflicts with any other prose doc, this document wins.
- Any formatter behavior change or bugfix must align with this document.
- If behavior changes intentionally, update this document in the same PR and record the rationale in `docs/technical-decision-register.md`.

## Scope

These rules define output shape for Java formatting in `modules/core`.

- Public configuration knobs are:
  - `indentStyle`
  - `indentSize`
  - `lineLength`
  - `wrapStyle`
  - `closingParenOnNewLine`
  - `trailingCommas`
  - `javaLanguageLevel`
- Continuation indent is derived as `2 * indentSize` and is not configurable.
- Any new public knob requires a TDR entry and updates to this file.

## Normative Rules

### Rule 1: Idempotency is mandatory

`format(format(x)) == format(x)` must hold for valid inputs.

### Rule 2: Braces and brace placement

- Control-flow bodies (`if/else/for/for-each/while/do`) are always braced.
- Opening braces use K&R style (same line as declaration/header).

### Rule 3: Indentation

- Block indentation is controlled by `indentStyle` + `indentSize`.
- Wrapped continuation lines are indented by `2 * indentSize` relative to the current block indentation. This follows the Oracle/IntelliJ convention and ensures parameters are always visually distinct from the method body.

### Rule 4: Line length and wrapping trigger

- `lineLength` is the target width.
- If inline rendering exceeds `lineLength`, wrapping rules apply.
- If no safe break point exists, long lines may remain over limit.

### Rule 5: WrapStyle must be construct-uniform

`wrapStyle` semantics are uniform across all wrapped constructs (arguments, parameters, type clauses, type parameters, chain segments, binary chains, array initializers, enum constants, and similar lists):

- `wide`: greedily pack items while respecting width.
- `balanced`: either fully inline if it fits, or one item/operator segment per continuation line.
- `narrow`: always one item/operator segment per continuation line once in wrapped form.

### Rule 6: Wrapped binary/operator chains

- Wrapped binary/logical chains place operators at the start of continuation lines.
- Operator chain wrapping must follow Rule 5.

### Rule 7: Method chains

- A wrapped method chain places each chain segment on its own continuation line with leading `.`.
- Chain indentation follows Rule 3 and Rule 5.

### Rule 8: Closing delimiter placement

For wrapped argument/parameter-like lists:

- `closingParenOnNewLine=true`: closing delimiter is on its own line at the opener's indentation column.
- `closingParenOnNewLine=false`: closing delimiter remains on the final content line.

### Rule 9: Blank lines

- Collapse multiple blank lines to at most one.
- No blank line immediately after an opening brace.
- No blank line immediately before a closing brace.

### Rule 10: Comments and annotation safety

- Preserve comment semantics and placement intent.
- Preserve type-use annotation semantics and declaration annotation correctness.
- Formatting must not change program meaning.

## Change Control

Any PR that changes formatter output shape must include:

1. tests demonstrating intended behavior,
2. showroom goldens regenerated when applicable,
3. updates to this document if the normative rule set changed,
4. a TDR entry when policy (not just bug parity) changed.
