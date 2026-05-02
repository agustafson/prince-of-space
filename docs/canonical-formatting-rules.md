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

## Rule index

| Rule | Summary |
|------|---------|
| [Rule 1](#rule-1-idempotency-is-mandatory) | Idempotency |
| [Rule 2](#rule-2-braces-and-brace-placement) | Braces (always braced bodies; K&R `{`) |
| [Rule 3](#rule-3-indentation) | Block indent + continuation indent (`2 * indentSize`; chains exception in Rule 7) |
| [Rule 4](#rule-4-line-length-and-wrapping-trigger) | Line length / wrap trigger |
| [Rule 5](#rule-5-wrapstyle-must-be-construct-uniform) | `wrapStyle` uniformity (+ enum exception) |
| [Rule 6](#rule-6-wrapped-binaryoperator-chains) | Wrapped binary and operator chains |
| [Rule 7](#rule-7-method-chains) | Method chains |
| [Rule 8](#rule-8-closing-delimiter-placement) | Closing `)` / delimiter placement |
| [Rule 9](#rule-9-blank-lines) | Blank lines |
| [Rule 10](#rule-10-comments-and-annotation-safety) | Comments and annotations |

## Normative Rules

### Rule 1: Idempotency is mandatory

`format(format(x)) == format(x)` must hold for valid inputs.

### Rule 2: Braces and brace placement

- Control-flow bodies (`if/else/for/for-each/while/do`) are always braced.
- Opening braces use K&R style (same line as declaration/header).
- **Wrapped declaration headers (`extends` / `implements` / `permits` / `throws`):** when a class / interface / record / enum header wraps because a type clause does not fit on one line, the body's opening `{` follows the same `closingParenOnNewLine` policy used by Rule 8 for closing delimiters: with `closingParenOnNewLine=true` the `{` lands on its **own line** at the declaration's block-indent column; with `closingParenOnNewLine=false` it **trails the last clause item** inline. This preserves the K&R intent (visible body opener) while keeping the `{` consistent with how every other wrapped delimited structure is closed.
- **Labeled statements:** a labeled statement (`label: stmt`) prints the label and the labeled statement opener on the **same line** (`label: for (…) {`, `label: while (…) {`). Header and body wrapping of the statement then follow that statement's own rules unchanged.

### Rule 3: Indentation

- Block indentation is controlled by `indentStyle` + `indentSize`.
- Wrapped continuation lines are indented by `2 * indentSize` relative to the current block indentation. This follows the Oracle/IntelliJ convention and ensures parameters are always visually distinct from the method body.
- For wrapped delimited argument/parameter-like lists that open on an already-continued line, that `2 * indentSize` continuation step is measured from the line's effective continuation start column (for example after `?` / `:` or wrapped binary operators), not from natural block indent.
- Method chain continuations are an exception (see Rule 7): they use a single `indentSize` step rather than `2 * indentSize`, because chain segments are visually self-delimiting (each starts with `.`).

### Rule 4: Line length and wrapping trigger

- `lineLength` is the target width.
- If inline rendering exceeds `lineLength`, wrapping rules apply.
- If no safe break point exists, long lines may remain over limit.

### Rule 5: WrapStyle must be construct-uniform

`wrapStyle` semantics are uniform across all wrapped constructs (arguments, parameters, type clauses, type parameters, chain segments, binary chains, array initializers, and similar lists):

- **Type clauses (`extends`, `implements`, `permits`, `throws`):** the same inline-fit check applies to every clause kind for a given `wrapStyle`, including `narrow` — a clause is emitted inline when it fits within `lineLength`; forced wrapping is not applied merely because `wrapStyle == narrow` when the clause would still fit on one line.

- `wide`: greedily pack items while respecting width. An item that carries a line comment (`//`) forces a line break after it, since the comment claims the rest of the line.
- `balanced`: either fully inline if it fits, or one item/operator segment per continuation line.
- `narrow`: always one item/operator segment per continuation line once in wrapped form.

**Enum constant lists (explicit exception):** `enum` constant declarations are never collapsed onto a shared line (`{ A, B, C }` on one line never appears; `wrapStyle` does **not** apply greedy packing across constants). Each constant occupies its own line after the enum’s `{` (comma-separated, then `;` when declarations follow). Empty enums remain `enum E { }` compatible with Rule 9 (no blank lines inside empty blocks beyond what the formatter already coalesces). See **TDR-023**.

**Per-constant anonymous bodies:** when one or more enum constants carry an anonymous class body (`CONST { … }`), the trailing comma between constants and the `;` introducing later member declarations land on **their own line** at the constant's indent column, regardless of `wrapStyle` or `closingParenOnNewLine`. The body braces of each constant therefore visually close before the separator that follows them.

### Rule 6: Wrapped binary/operator chains

- Wrapped binary/logical chains place operators at the start of continuation lines.
- Operator chain wrapping must follow Rule 5.

### Rule 7: Method chains

- A wrapped method chain places each chain segment on its own continuation line with leading `.`.
- Chain segments are indented by exactly **`indentSize`** (one block-indent unit) relative to the receiver's line, rather than the `2 * indentSize` continuation indent used for delimited list continuations (Rule 3). The leading `.` on every segment provides its own visual delimiter, so a single indent step is sufficient and reduces excess depth in deeply chained code (see TDR-015).
- When a wrapped method chain appears as an operand of a wrapped binary/logical chain (Rule 6), its segments are indented by one additional `indentSize` past the operator-line indent, so the chain remains visually distinct from the operator that introduces it.
- Wrapping decisions for chain segments still follow Rule 5 (`wide` / `balanced` / `narrow`).
- **Cast expressions in a chain receiver:** cast expressions (`(T) expr`) and the parenthesized cast wrappers around them are printed **inline** as part of the chain's receiver expression — they do not introduce their own indentation step. A wrapped chain whose receiver begins with one or more casts indents subsequent `.segment(…)` calls relative to the chain's normal anchor (per the bullets above), not relative to any cast boundary.

### Rule 8: Closing delimiter placement

For wrapped argument/parameter-like lists:

- `closingParenOnNewLine=true`: closing delimiter is on its own line at the opener's indentation column.
  - For nested wrapped calls with co-line openers (for example `outer(inner(`), closers may compact on one closer line (`));`, `)));`) instead of stacking one `)` per line.
  - For line-separated nested openers, closers remain on separate lines aligned to their corresponding opener lines.
- `closingParenOnNewLine=false`: closing delimiter remains on the final content line.
- **Trailing-lambda exception (TDR-021):** when the **last argument** of a wrapped call is a lambda — block- or expression-bodied — the leading arguments and the lambda header (`() -> {`, `(a, b) -> {`, `s ->`, `value ->`, etc.) stay on the call line, the lambda body wraps according to its own rules (block body via its block indent; expression body via the receiver chain or other inner wrap mechanic), and the closing `)` follows the lambda body inline (`});`, `))`, `.last())`, etc.) at the call's indent column **regardless of `closingParenOnNewLine`**. This matches palantir-java-format / Prettier / ktlint conventions and keeps the lambda visually coupled to the call. The opener line may slightly exceed `lineLength` as a soft tradeoff; the alternative (lambda header on its own line) reads worse. The exception does **not** apply when a *leading* argument is itself a block-bodied lambda or carries a leading line/block comment — those calls fall back to the standard per-arg wrapping path.
- **Wrapped lambda parameter list (TDR-022):** when a parenthesized lambda's formal parameter list itself wraps (multi-parameter list that does not fit), the closing `) ->` always lands on its own line aligned to the opener `(` column — same shape as a wrapped declaration parameter list — **regardless of `closingParenOnNewLine`**. Formal parameters cannot trail off the end of a long line because the `->` arrow needs to read as a continuation marker; pinning the closer to the opener column matches the general Rule 8 placement and keeps the param-list shape consistent with constructor/method declarations.

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
