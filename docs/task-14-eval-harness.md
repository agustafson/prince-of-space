# Task 14 — Enhanced Real-World Evaluation Harness

**Status:** Not started
**Priority:** Medium — validates the formatter against production-scale Java code
**Effort:** Medium

---

## Goal

Extend the existing `OptionalRealWorldCheckoutFormatTest` into a proper evaluation harness that:

1. Runs multiple `FormatterConfig` permutations against two well-known Java codebases
2. Checks idempotency and parse-error-free operation (hard assertions = test failures)
3. Reports over-long non-comment lines as warnings (informational, never a failure)
4. Emits a structured Markdown report to `docs/eval-results/`

---

## Background

`OptionalRealWorldCheckoutFormatTest` (enabled by `PRINCE_REAL_WORLD_ROOT`) already does a
single-config, default-only, idempotency-only run. This task replaces / supersedes it with a
richer harness while keeping the same `@EnabledIfEnvironmentVariable` guard so it never runs
in default CI.

---

## Target codebases

| Project | Repo | Shallow clone command |
|---------|------|-----------------------|
| **Guava** | `google/guava` | `git clone --depth=1 https://github.com/google/guava /tmp/eval/guava` |
| **Spring Framework** | `spring-projects/spring-framework` | `git clone --depth=1 https://github.com/spring-projects/spring-framework /tmp/eval/spring-framework` |

**Why these two:**

- **Guava** — ~750 `.java` files / ~130 K LOC. Native line limit is 100 chars (google-java-format).
  Heavy generics, rich annotations, functional patterns, complex enums. Running with
  `preferred=80 / max=100` forces the formatter to actively wrap code that is already at the
  edge of google-java-format's limit — maximum stress on the wrapping engine.

- **Spring Framework** — ~3 000+ `.java` files. Native line limit is 120 chars. Dense
  `@Component` / `@Bean` / `@Conditional` stacks (annotation arranger stress), extensive
  Javadoc (comment preservation stress), varied interface-hierarchy patterns.

---

## Config permutations

Run **9 configs** per project (3 width bands × 3 wrap styles):

| Name | `preferredLineLength` | `maxLineLength` | `wrapStyle` | Rationale |
|------|-----------------------|-----------------|-------------|-----------|
| `aggressive-wide` | 80 | 100 | `WIDE` | Max wrapping stress on Guava |
| `aggressive-balanced` | 80 | 100 | `BALANCED` | Same, balanced style |
| `aggressive-narrow` | 80 | 100 | `NARROW` | Same, narrow style |
| `moderate-wide` | 100 | 120 | `WIDE` | Sensible defaults for many projects |
| `moderate-balanced` | 100 | 120 | `BALANCED` | **Primary eval config** |
| `moderate-narrow` | 100 | 120 | `NARROW` | |
| `default-wide` | 120 | 150 | `WIDE` | Formatter's own defaults |
| `default-balanced` | 120 | 150 | `BALANCED` | Verifies stability when wrapping rarely fires |
| `default-narrow` | 120 | 150 | `NARROW` | |

All other config knobs stay at defaults (`IndentStyle.SPACES`, `indentSize=4`,
`continuationIndentSize=4`, `closingParenOnNewLine=true`, `trailingCommas=false`,
`javaLanguageLevel=JAVA_17`).

---

## Implementation

### 1. Replace `OptionalRealWorldCheckoutFormatTest`

Delete the existing class and replace it with `RealWorldEvalTest` in the same package
(`io.princeofspace`, `core/src/test/java/io/princeofspace/RealWorldEvalTest.java`).

**Environment variables:**

| Variable | Meaning |
|----------|---------|
| `PRINCE_EVAL_ROOTS` | Comma-separated absolute paths to project checkouts, e.g. `/tmp/eval/guava,/tmp/eval/spring-framework`. Test is skipped when unset. |
| `PRINCE_EVAL_REPORT_DIR` | Directory to write Markdown reports (default: `docs/eval-results` relative to repo root). Created if absent. |

### 2. Per-file logic (inner loop)

For each `(project, config)` pair, walk `.java` files (skip `build/`, `.gradle/`, `.git/`,
`generated/`, `generated-sources/`). For each file:

```
1. source = Files.readString(path)

2. try:
       once = formatter.format(source)
   catch FormatterException e:
       record PARSE_ERROR(path, e.getMessage())
       continue

3. twice = formatter.format(once)
   if twice != once:
       record IDEMPOTENCY_FAIL(path, diff summary)

4. for each line in once.lines():
       if line.length() > config.maxLineLength():
           trimmed = line.stripLeading()
           if NOT (trimmed.startsWith("//")
                   || trimmed.startsWith("*")
                   || trimmed.startsWith("import ")
                   || trimmed.startsWith("package ")):
               record OVER_LONG_LINE(path, lineNumber, line.length())

5. record FILE_OK or FILE_REFORMATTED (whether once != source)
```

### 3. Failure policy

| Condition | Test outcome |
|-----------|-------------|
| Any `PARSE_ERROR` | **Test fails** — print all paths and messages |
| Any `IDEMPOTENCY_FAIL` | **Test fails** — print all paths |
| `OVER_LONG_LINE` entries | **Warning only** — printed to stdout; test passes |

Collect all failures before asserting so a single run surfaces every problem at once
(use `assertThat(...).isEmpty()` with a multi-line message, not fail-fast asserts).

### 4. Reporting (`EvalReport`)

Create a simple `EvalReport` record/class (package-private, same directory) that holds the
aggregated stats for one `(project, config)` run and can serialise itself to Markdown.

After all runs complete, write one Markdown report per invocation:

**Output path:** `${PRINCE_EVAL_REPORT_DIR}/<yyyy-MM-dd>.md`
(overwrite if the file already exists — a re-run on the same day replaces the previous report)

**Report format:**

```markdown
# Prince of Space — Eval Report
Date: 2026-04-13
Formatter version: (read from `core/build/resources/main/version.txt` if it exists, else "dev")

## guava @ abc1234  (git rev-parse HEAD of the clone)

### aggressive-balanced (preferred=80, max=100, wrapStyle=BALANCED)
- Files: 748 scanned, 748 attempted
- Parse errors: 0
- Idempotency failures: 0
- Reformatted: 631 (84%)
- Already clean: 117 (16%)
- Over-long non-comment lines: 3 (see warnings below)
- Time: 4.2 s (5.6 ms/file avg)

<details><summary>Over-long line warnings</summary>

- `guava/guava/src/com/google/common/collect/ImmutableMap.java:412` — 153 chars
  (long string literal)

</details>

### moderate-balanced (preferred=100, max=120, wrapStyle=BALANCED)
...

## spring-framework @ def5678
...

## Summary

| Project | Config | Parse errors | Idempotency failures | Over-long lines |
|---------|--------|-------------|----------------------|-----------------|
| guava | aggressive-balanced | 0 | 0 | 3 |
| guava | moderate-balanced | 0 | 0 | 1 |
...
```

Use `git -C <cloneDir> rev-parse --short HEAD` (via `ProcessBuilder`) to capture the commit
hash of each clone. If the command fails, fall back to `"unknown"`.

### 5. Gradle wiring

No new module is needed — add a `evalTest` task to `core/build.gradle.kts`:

```kotlin
val evalTest by tasks.registering(Test::class) {
    description = "Runs the real-world evaluation harness (requires PRINCE_EVAL_ROOTS to be set)."
    group = "verification"
    useJUnitPlatform { includeTags("eval") }
    // inherit the normal test classpath
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    // forward env vars so the test can read them
    environment("PRINCE_EVAL_ROOTS", System.getenv("PRINCE_EVAL_ROOTS") ?: "")
    environment("PRINCE_EVAL_REPORT_DIR", System.getenv("PRINCE_EVAL_REPORT_DIR") ?: "")
}
```

Annotate `RealWorldEvalTest` with `@Tag("eval")` so the default `test` task never picks it up.

---

## Running the eval (operator instructions)

```bash
# 1. Clone targets (one-time, outside the repo)
git clone --depth=1 https://github.com/google/guava /tmp/eval/guava
git clone --depth=1 https://github.com/spring-projects/spring-framework /tmp/eval/spring-framework

# 2. Run the harness
export PRINCE_EVAL_ROOTS=/tmp/eval/guava,/tmp/eval/spring-framework
export PRINCE_EVAL_REPORT_DIR=$(pwd)/docs/eval-results
./gradlew :core:evalTest

# 3. Review output
cat docs/eval-results/$(date +%F).md
```

To re-run after fixing bugs, repeat step 2. The report is overwritten.

---

## Verification checklist

- [ ] `./gradlew :core:test` still passes (eval test is excluded from default task)
- [ ] `./gradlew :core:evalTest` with both roots set runs without `BUILD FAILED`
- [ ] Zero parse errors against Guava with `aggressive-balanced` config
- [ ] Zero idempotency failures across all 18 runs (9 configs × 2 projects)
- [ ] Report written to `docs/eval-results/<date>.md`
- [ ] `./gradlew build` passes (Spotless, Checkstyle, SpotBugs, Error Prone)

---

## Expected problem areas

When failures occur, the most likely root causes are:

| Symptom | Likely location |
|---------|----------------|
| Parse error on Guava generic code | JavaParser language level mismatch — try `JAVA_17` or `JAVA_21` |
| Idempotency failure on nested generics | `printTypeParameters` in `PrincePrettyPrinterVisitor` |
| Idempotency failure after blank-line changes | `BlankLineNormalizer` for nested types (see Task 12) |
| Over-long line on annotation | `AnnotationArranger` or annotation argument printing |
| Over-long line on Javadoc | Expected — comments are never truncated; no action needed |

Fix issues in `core/`, run `./gradlew :core:test` between each fix to avoid regressions, then
re-run `:core:evalTest` to verify.

---

## Publishing results

After a clean run (zero errors and zero idempotency failures):

1. Commit `docs/eval-results/<date>.md` to the repo.
2. Add a "Tested on" section to `README.md` referencing the report.

The report commit message should follow the project convention:
`eval: clean run against guava + spring-framework (<date>)`
