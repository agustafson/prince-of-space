# Robustness harness (real-world trees)

## Checked-in corpus (always on)

`ExamplesCorpusFormatTest` walks `examples/outputs/**` and `examples/inputs/**` and asserts every
`.java` file satisfies `format(format(x)) == format(x)` (language level from path segments `java8`,
`java17`, or `java21`).

## Optional checkout eval harness (manual / CI with clones)

Set `PRINCE_EVAL_ROOTS` to a comma-separated list of absolute checkout paths. Run:

```bash
export PRINCE_EVAL_ROOTS=/tmp/eval/guava,/tmp/eval/spring-framework
export PRINCE_EVAL_REPORT_DIR=$(pwd)/docs/eval-results
./gradlew :core:evalTest
```

The eval test is skipped when `PRINCE_EVAL_ROOTS` is unset/blank. It scans `.java` files while
skipping common build and generated paths (`build/`, `.gradle/`, `.git/`, `generated/`,
`generated-sources/`) and writes a daily Markdown report under `docs/eval-results/`.

## Full-tree scripts

Cloning Guava, Spring Boot, or Commons and formatting every file is intentionally not automated in
default CI (network, time, and moving upstream). Use the environment-variable test above after a
manual `git clone --depth 1`.
