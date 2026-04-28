Goal: Make Rule 3 continuation behavior match the properties above and align showroom Java 25 / balanced / closingParenOnNewLine=true (and related goldens) without breaking
idempotency.


Phase 0 — Baseline ✅

1. ✅ Run ./gradlew :core:test --tests io.princeofspace.internal.ContinuationIndentStepPropertyTest — two failures confirmed (closing `) ->` indent 24 vs expected 20; line 16→17 jump 16 vs ≤8).
2. ✅ Read PrincePrettyPrinterVisitor, ArgumentListFormatter, and lambda / parenthesized parameter helpers (continuationIndentSize, continuationLineStartColumn, printCont).
3. ✅ Re-read docs/canonical-formatting-rules.md Rule 3 and Rule 7; mapped continuation indent for argument lists, parenthesized lambdas, and nested wrapped lists.


Phase 1 — Parenthesized lambda ( … ) -> (scenario 44 / line 620) ✅ — narrow property passes after fixing `padToColumn0` auto-indent double-counting and aligning lambda close-paren to `openParen + indentSize` on its own line.

1. ✅ Trace formatting from standalone `(` through parameter lines to `) ->`.
2. ✅ Found `) ->` used `openParen + 2 * indentSize` (24) instead of `openParen + indentSize` (20).
3. ✅ `printLambdaParameters` now always closes on its own line at `openParen + indentSize`; `padToColumn0` materializes auto-indent before measuring.
4. ✅ Narrow property test passes.
5. ✅ Goldens regenerated with `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens`.


Phase 2 — Single-line indent jump ≤ 2 * indentSize (global property) ✅ — `padToColumn0` fix removed the `( → first param` double-step within the lambda block; the broad property test passes.

1. ✅ Re-ran broad property after Phase 1; `padToColumn0` empty-print fix removes the +16 jump.
2. ✅ No further continuation double-application detected in the synthetic input.
3. ✅ ContinuationIndentStepPropertyTest passes.
4. ✅ Full `./gradlew :core:test` green (296 tests, 2 skipped — RegenerateShowroomGoldens harness gated on env var).


Phase 3 — Scenario 11 (groupingBy inner args) ✅ — first arg now lands at `+8` (12→20) after `continuationLineStartColumn` is invalidated when its recorded line no longer matches the printer cursor.

1. ✅ After regeneration, lines 174–183 jump from 12 → 20 (+8) — within the `2 * indentSize` cap.
2. ✅ No additional fix required beyond the cross-statement leak fix described in Phase 4.


Phase 4 — Scenario 54 (nested wrapped calls) ✅ — root cause was `continuationLineStartColumn` leaking across statements; whichever statement happened to consume it next ended up double-stepping by `2 * indentSize` (e.g. scenario 50 first call at +16 instead of +8). Fix: also track `continuationLineStartLine`; `enterWrappedDelimitedListScope()` only consumes the recorded column when the printer is still on the same line.

1. ✅ Inspected 772–782; nested call indent now stacks 8 → 16 → 24 with each transition ≤ `2 * indentSize`.
2. ✅ Property tests pass for both narrow and broad checks.
3. ✅ Closer placement still correct (closer at the openers' column for stacked nested calls).


Phase 5 — Scenario 51 (lines 737–742, optional simplification) ✅ kept as-is

1. ✅ Lines 737→738 jump +8 (`.anyMatch(...` 12 → `&& s` 20); 738→739 jump +4 (`&& s` 20 → `.chars()` 24); both within the `2 * indentSize` cap.
2. ✅ Optional left-shift not taken: Rule 7 still requires the chain under `&& s` to indent past the operator line, and the current shape reads correctly. Skipping per the plan ("only take this if readability and Rule 7 still hold").


Phase 6 — Scenario 52 ✅ already compliant

1. ✅ Lines 745–753 jump 8 → 16 → 16 → 24 (all within `2 * indentSize`).
2. ✅ Property tests + FormatterShowcaseGoldenTest pass; no further changes required.


Phase 7 — Documentation and PR hygiene

1. Document continuation-line-start-column line-validity invariant inline (Phase 4 fix has Javadoc on the new field; canonical rules unchanged because the rule itself is normative-stable — the formatter behavior is now closer to the rule, not further from it).
2. Run `./gradlew build` before pushing.
3. Stage new tracked files (`docs/implementation-plan-max-indent.md`, `ContinuationIndentStepPropertyTest.java`, regenerated goldens) explicitly.

────────────────────────────────────────

Note: ContinuationIndentStepPropertyTest is now green; goldens for all language levels and wrap configurations were regenerated to match the corrected behavior. No public configuration knobs added or changed; canonical rules document is untouched.
