# Formatter throughput — Spring Framework corpus

Date: 2026-05-03
Corpus root (local): `/tmp/spring-framework-bench`
Git revision: `d9ecf94`
Files formatted: **9204**
JVM: **21.0.2+13-58**

Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Other JVM formatters use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons.

## Results

| Formatter | Wall time | ms / file | Failures |
|-----------|-----------|-----------|----------|
| Prince of Space | 181 s | 19.74 | 0 |
| Google Java Format (AOSP) | 99 s | 10.79 | 0 |
| Palantir Java Format (AOSP) | 101 s | 11.03 | 0 |

## Tool versions

- Prince of Space: **0.1.1-SNAPSHOT** (Gradle `version` when launched via `./gradlew :formatter-benchmark:run`)
- Google Java Format: 1.27.0 (AOSP)
- Palantir Java Format: 2.71.0 (AOSP)
- Prettier: 3.4.2 + prettier-plugin-java 2.6.6 (via `npx`)


_Prettier leg skipped (`PRINCE_BENCH_SKIP_PRETTIER=true`)._

Regenerate (after cloning Spring Framework):

```bash
export PRINCE_BENCH_ROOT=/path/to/spring-framework
./gradlew :formatter-benchmark:run
```
