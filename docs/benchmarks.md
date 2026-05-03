# Performance notes

## Smoke tests

`FormatPerformanceSmokeTest` asserts that formatting a large synthetic class (~800 methods) and many
small passes completes within generous wall-clock bounds on CI.

## Corpus throughput comparison (`:formatter-benchmark`)

The Gradle module **`formatter-benchmark`** formats every `.java` file under `PRINCE_BENCH_ROOT` with
Prince of Space, Google Java Format (AOSP), Palantir Java Format (AOSP), and optionally Prettier +
prettier-plugin-java (`npx`). Results are written to [`perf-results/spring-framework.md`](perf-results/spring-framework.md)
(use `PRINCE_BENCH_REPORT` for another path).

```bash
export PRINCE_BENCH_ROOT=/path/to/spring-framework
./gradlew :formatter-benchmark:run
```

Google Java Format / Palantir need JDK-internal javac exports — the `run` task adds the standard
`--add-exports` flags (same idea as running those formatters on JDK 16+). Prettier rewrites the
checkout; use a disposable clone or set `PRINCE_BENCH_SKIP_PRETTIER=true`.

## README automation

- `./gradlew assemble` runs **`syncReadmeVersions`** locally so Gradle/Maven coordinates in `README.md`
  track `gradle.properties` `version`.
- GitHub Actions sets `CI=true`; **`syncReadmeVersions` / `syncReadmePerfSection` are skipped there unless**
  **`RUN_README_SYNC=true`** (avoids three parallel matrix jobs rewriting `README.md`).
- **`.github/workflows/readme-benchmark.yml`** (push to `main` / `master`) clones Spring Framework,
  runs `:formatter-benchmark:run`, then **`syncReadmePerfSection`** + **`syncReadmeVersions`**, and commits
  `README.md` + `docs/perf-results/spring-framework.md` with **`[skip ci]`** so the bot push does not loop CI.
- **`.github/workflows/sync-readme-versions.yml`** runs the same coordinate sync when **`gradle.properties`**
  changes on `main` / `master`.
- **`syncReadmePerfSection`** replaces the `<!-- sync:perf:start -->` … `<!-- sync:perf:end -->` block using the
  checked-in report file.

## Future work

- Add a JMH subproject for steady-state throughput (lines/sec) and allocation profiles.
- Revisit `est()` / subtree string width estimates in `PrincePrettyPrinterVisitor` if profiling shows
  hot allocation from `toString()` on deep trees.
