# Real-World Evaluation

Prince of Space is validated against two large, well-known Java codebases to catch formatting and idempotency regressions that synthetic tests miss.

## Target codebases

| Project | Size | Why |
|---------|------|-----|
| **Guava** (`google/guava`) | ~3,200 `.java` files | Heavy generics, functional patterns, complex enums. Native line limit is 100 chars (google-java-format), so our wrapping engine is stressed heavily. |
| **Spring Framework** (`spring-projects/spring-framework`) | ~9,200 `.java` files | Dense annotation stacks (`@Component`, `@Bean`, `@Conditional`), extensive Javadoc, varied interface hierarchies. |

## Latest results

Evaluated on 2026-04-15 with `lineLength=120`, `wrapStyle=BALANCED` (`w120-balanced`).

| Project | Files | Parse errors | Idempotency failures | Over-long lines | Time |
|---------|-------|-------------|----------------------|-----------------|------|
| Guava @ `ce39d2b` | 3,221 | **0** | **0** | 59 | 415s |
| Spring Framework @ `1787d3e` | 9,198 | **0** | **0** | 235 | 161s |

**Zero parse errors** and **zero idempotency failures** across 12,419 files.

Over-long lines are informational warnings — they occur in constructs that have no safe wrap point (very long string literals, generated data files, deeply nested generic signatures). See the full report [eval-results/2026-04-17.md](eval-results/2026-04-17.md) for per-file details.

[`eval-results/2026-04-17.md`](eval-results/2026-04-17.md) aggregates runs across line lengths and wrap styles, confirming zero parse errors and zero idempotency failures for each configuration shown there.

## Running the eval harness

### 1. Clone targets (one-time, outside the repo)

```bash
git clone --depth=1 https://github.com/google/guava /tmp/eval/guava
git clone --depth=1 https://github.com/spring-projects/spring-framework /tmp/eval/spring-framework
```

### 2. Run

Set **one** line length and wrap style per invocation (CI sets these from the workflow matrix):

```bash
export PRINCE_EVAL_ROOTS=/tmp/eval/guava,/tmp/eval/spring-framework
export PRINCE_EVAL_LINE_LENGTH=120
export PRINCE_EVAL_WRAP_STYLE=BALANCED
export PRINCE_EVAL_REPORT_DIR=$(pwd)/docs/eval-results
./gradlew :core:evalTest
```

`PRINCE_EVAL_WRAP_STYLE` is case-insensitive (`BALANCED`, `balanced`, etc.).

The test is skipped when `PRINCE_EVAL_ROOTS` is unset. It scans `.java` files while skipping common build and generated paths (`build/`, `.gradle/`, `.git/`, `generated/`, `generated-sources/`).

Optional **`PRINCE_EVAL_REPORT_SLUG`** (ASCII alphanumerics, `-`, `_`, max 64 chars) writes
`docs/eval-results/<date>-<slug>.md` instead of `<date>.md`, so parallel corpus runs keep
separate files (this is what CI uses).

### 3. Review

```bash
cat docs/eval-results/$(date +%F).md
# or, when using PRINCE_EVAL_REPORT_SLUG=guava:
# cat docs/eval-results/$(date +%F)-guava.md
```

Without a slug, reports are overwritten on re-run for the same day; with a slug, only
same-day re-runs for that slug overwrite.

## Checked-in corpus (always on)

`ExamplesCorpusFormatTest` walks `examples/outputs/**` and `examples/inputs/**` on every `./gradlew :core:test` run and asserts every `.java` file satisfies `format(format(x)) == format(x)`.

## Failure policy

| Condition | Test outcome |
|-----------|-------------|
| Any parse error | **Fails** — all paths and messages printed |
| Any idempotency failure | **Fails** — all paths printed |
| Over-long non-comment lines | **Warning only** — printed to stdout; test passes |

## Release gating

The eval is mandatory for releases. The `release` workflow runs **`external-eval`**
as a **matrix** (Spring Framework and Guava × three `lineLength` values × three wrap styles);
the publish job declares `needs: external-eval`, so a
failed leg blocks publishing even on dry runs. See
[RELEASING.md — External eval gate](https://github.com/agustafson/prince-of-space/blob/main/RELEASING.md#external-eval-gate-mandatory)
on GitHub for recovery steps.

A lighter **`external-eval-smoke`** matrix (`lineLength=120`, `wrapStyle=BALANCED` only) also runs on every
push and pull request for fast feedback; the full matrix runs weekly and
on-demand via `workflow_dispatch`. See `.github/workflows/external-eval.yml`.

## Config permutations

The full eval runs one job per combination of `lineLength` ∈ {80, 100, 120} and
`wrapStyle` ∈ {WIDE, BALANCED, NARROW}. Reports label each run as `w<lineLength>-<wrapStyle>` (e.g. `w120-balanced`).
