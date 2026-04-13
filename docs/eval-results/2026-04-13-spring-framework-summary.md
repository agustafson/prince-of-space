# Spring Framework Eval Analysis (2026-04-13)

Source run:

- Command: `PRINCE_EVAL_ROOTS=/Users/gus/dev/projects/spring-framework ./gradlew :core:evalTest --rerun-tasks`
- Raw report: `docs/eval-results/2026-04-13.md`

## Executive takeaways

- The run was **not all failures**. A large subset of files format successfully.
- Across all 9 configs:
  - `9206` files scanned each run
  - `3495` parse/runtime formatter failures each run
  - `5711` files formatted successfully each run (`~62.0%` of total)
  - `5651` to `5663` files were both parse-successful and idempotent (`~61.4% to ~61.5%` of total)
- Primary blocker is a single dominant runtime failure shape: `NoSuchElementException: No value present`.

## What succeeded

For every config permutation:

- **Parse+format success:** `5711 / 9206` files
- **Parse+format+idempotent success:** `5651` to `5663` files

Best idempotency result configs (same count):

- `aggressive-balanced`
- `aggressive-narrow`
- `moderate-balanced`
- `moderate-narrow`
- `default-balanced`
- `default-narrow`

Each of these yielded `5663` idempotent files.

## Failure profile

### 1) Parse/runtime failures (hard failures)

- Count: `3495` per config (`31,455` entries across all 9 config runs)
- Dominant error message frequencies from the failing test output:
  - `31,433` — `unexpected formatter error: NoSuchElementException: No value present`
  - `18` — `Parse failed: ...`
  - `3` — `unexpected formatter error: IllegalStateException: Attempt to indent less than the previous indent.`

Interpretation:

- The parse failure bucket is overwhelmingly a formatter runtime bug, not a broad JavaParser syntax support gap.
- This likely indicates one or more AST shape assumptions that break repeatedly across Spring code.

### 2) Idempotency failures (hard failures)

- `48` to `60` per config (on already parse-successful files)
- These are much smaller than parse/runtime failures, but still release-blocking.
- Current Markdown report contains counts but not full file lists for idempotency failures.

### 3) Over-long non-comment lines (warning-only)

- Config-sensitive and expected to vary by width:
  - `aggressive-*`: `2267` to `2922`
  - `moderate-*`: `560` to `734`
  - `default-*`: `54` to `74`
- This is informational and not currently blocking.

## Hotspot modules (parse/runtime failure concentration)

Top root modules represented in parse/runtime failures:

- `spring-test` (highest concentration)
- `spring-web`
- `spring-context`
- `spring-core`
- `spring-webmvc`
- `spring-webflux`

Many early-listed failures are in `spring-webflux` tests, making it a good first triage target.

## Recommended fix plan (next steps)

1. **Capture first full stack trace for `NoSuchElementException`**
   - Temporarily add fail-fast debug mode in the eval harness (or run a focused reproduction test) to print stack for first offending file.
   - Current harness intentionally records and continues, which hides call-site detail.

2. **Minimize one representative Spring failure**
   - Start with one `spring-webflux` file from the failing list and reduce to a minimal reproducer.
   - Add a targeted regression test in `core/src/test/java/io/princeofspace/`.

3. **Fix dominant runtime assumption in formatter visitor**
   - Based on likely location, investigate optional-node access patterns in `PrincePrettyPrinterVisitor`.
   - Re-run `:core:test` after each fix, then re-run `:core:evalTest` against Spring.

4. **Expose detailed idempotency file list in report**
   - Extend `EvalReport` to include `<details>` sections for parse errors and idempotency failures (possibly capped to top N plus total count).
   - This will speed root-cause clustering for the remaining 48–60 idempotency regressions.

5. **Then run Guava once clone completes**
   - Validate whether the same dominant runtime failure reproduces on Guava or is Spring-specific.

## Bottom line

The Spring eval produced actionable signal immediately:

- Formatter already handles about **61.5%** of files end-to-end (parse + idempotent) across all config families.
- A **single dominant runtime exception class** accounts for almost all hard failures, which is a good sign for concentrated fixes rather than diffuse issues.
