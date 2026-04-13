# Robustness harness (real-world trees)

## Checked-in corpus (always on)

`ExamplesCorpusFormatTest` walks `examples/outputs/**` and asserts every golden `.java` file satisfies
`format(format(x)) == format(x)`. It also walks `examples/inputs/**` and asserts a single
`format(...)` succeeds (parse + print). Language level is inferred from path segments `java8`,
`java17`, or `java21`.

## Optional checkout (manual / CI with clone)

Set `PRINCE_REAL_WORLD_ROOT` to the absolute path of a Java project checkout. Run:

```bash
export PRINCE_REAL_WORLD_ROOT=/path/to/guava
./gradlew :core:test --tests io.princeofspace.OptionalRealWorldCheckoutFormatTest
```

The test is skipped when the variable is unset. It skips obvious build output paths (`build/`, `.git/`).

To batch against multiple projects, clone them under one parent and point the variable at that
parent, or run the test multiple times with different roots.

## Full-tree scripts

Cloning Guava, Spring Boot, or Commons and formatting every file is intentionally not automated in
default CI (network, time, and moving upstream). Use the environment-variable test above after a
manual `git clone --depth 1`.
