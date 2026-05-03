# Formatter throughput — Spring Framework corpus

Date: 2026-05-03
Corpus root (local): `/home/runner/work/prince-of-space/prince-of-space/spring-framework-bench`
Git revision: `d9ecf94`
Files formatted: **9204**
JVM: **21.0.10+7-LTS**
JVM formatter workers (Prince / Google / Palantir): **1**

Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Other JVM formatters use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons.

## Results

| Formatter | Wall time | ms / file | Failures |
|-----------|-----------|-----------|----------|
| Prince of Space (strict, fixed-point) | 58 s | 6.33 | 0 |
| Prince of Space (fast, single pass) | 23 s | 2.55 | 0 |
| Google Java Format (AOSP) | 27 s | 2.98 | 0 |
| Palantir Java Format (AOSP) | 25 s | 2.72 | 0 |

## Tool versions

- Prince of Space: **2.1.3-SNAPSHOT** (Gradle `version` when launched via `./gradlew :formatter-benchmark:run`)
- Google Java Format: 1.27.0 (AOSP)
- Palantir Java Format: 2.71.0 (AOSP)
- Prettier: 3.4.2 + prettier-plugin-java 2.6.6 (via `npx`)




_Prettier leg skipped (`PRINCE_BENCH_SKIP_PRETTIER=true`)._

Regenerate (after cloning Spring Framework):

```bash
export PRINCE_BENCH_ROOT=/path/to/spring-framework
./gradlew :formatter-benchmark:run
```
