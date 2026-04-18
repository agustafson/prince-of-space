# Real-World Evaluation

Prince of Space is validated against two large, well-known Java codebases to catch formatting and idempotency regressions that synthetic tests miss.

## Target codebases

| Project | Size | Why |
|---------|------|-----|
| **Guava** (`google/guava`) | ~3,200 `.java` files | Heavy generics, functional patterns, complex enums. Native line limit is 100 chars (google-java-format), so our wrapping engine is stressed heavily. |
| **Spring Framework** (`spring-projects/spring-framework`) | ~9,200 `.java` files | Dense annotation stacks (`@Component`, `@Bean`, `@Conditional`), extensive Javadoc, varied interface hierarchies. |

## Latest results

Evaluated on 2026-04-15 with default-balanced config (`preferredLineLength=120`, `maxLineLength=150`, `wrapStyle=BALANCED`).

| Project | Files | Parse errors | Idempotency failures | Over-long lines | Time |
|---------|-------|-------------|----------------------|-----------------|------|
| Guava @ `ce39d2b` | 3,221 | **0** | **0** | 59 | 415s |
| Spring Framework @ `1787d3e` | 9,198 | **0** | **0** | 235 | 161s |

**Zero parse errors** and **zero idempotency failures** across 12,419 files.

Over-long lines are informational warnings — they occur in constructs that have no safe wrap point (very long string literals, generated data files, deeply nested generic signatures). See the full reports in [`eval-results/`](eval-results/) for per-file details.

A full 9-config evaluation (3 width bands x 3 wrap styles) is also available in [`eval-results/2026-04-17.md`](eval-results/2026-04-17.md), confirming zero parse errors and zero idempotency failures across all configurations.

## Running the eval harness

### 1. Clone targets (one-time, outside the repo)

```bash
git clone --depth=1 https://github.com/google/guava /tmp/eval/guava
git clone --depth=1 https://github.com/spring-projects/spring-framework /tmp/eval/spring-framework
```

### 2. Run

```bash
export PRINCE_EVAL_ROOTS=/tmp/eval/guava,/tmp/eval/spring-framework
export PRINCE_EVAL_REPORT_DIR=$(pwd)/docs/eval-results
./gradlew :core:evalTest
```

The test is skipped when `PRINCE_EVAL_ROOTS` is unset. It scans `.java` files while skipping common build and generated paths (`build/`, `.gradle/`, `.git/`, `generated/`, `generated-sources/`).

### 3. Review

```bash
cat docs/eval-results/$(date +%F).md
```

Reports are overwritten on re-run for the same day.

## Checked-in corpus (always on)

`ExamplesCorpusFormatTest` walks `examples/outputs/**` and `examples/inputs/**` on every `./gradlew :core:test` run and asserts every `.java` file satisfies `format(format(x)) == format(x)`.

## Failure policy

| Condition | Test outcome |
|-----------|-------------|
| Any parse error | **Fails** — all paths and messages printed |
| Any idempotency failure | **Fails** — all paths printed |
| Over-long non-comment lines | **Warning only** — printed to stdout; test passes |

## Config permutations

The full eval runs 9 configs per project:

| Name | Preferred | Max | Wrap style | Rationale |
|------|-----------|-----|------------|-----------|
| `aggressive-wide` | 80 | 100 | Wide | Max wrapping stress |
| `aggressive-balanced` | 80 | 100 | Balanced | |
| `aggressive-narrow` | 80 | 100 | Narrow | |
| `moderate-wide` | 100 | 120 | Wide | Sensible defaults for many projects |
| `moderate-balanced` | 100 | 120 | Balanced | Primary eval config |
| `moderate-narrow` | 100 | 120 | Narrow | |
| `default-wide` | 120 | 150 | Wide | Formatter's own defaults |
| `default-balanced` | 120 | 150 | Balanced | Stability when wrapping rarely fires |
| `default-narrow` | 120 | 150 | Narrow | |
