# Formatter throughput — Spring Framework corpus

Date: 2026-05-04
Corpus root (local): `/tmp/spring-fw-bench`
Git revision: `39ff8e4`
Files formatted: **9204**
JVM: **21.0.2+13-58**
JVM formatter workers (Prince / Google / Palantir / Eclipse): **1**

Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Google Java Format and Palantir Java Format use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons. Eclipse JDT uses Spotless `eclipse()` defaults (`examples/external/outputs/eclipse/`).

## Results

| Formatter | Avg ms / file | Failures |
|-----------|-----------------|----------|
| Prince of Space (strict, fixed-point) | 3.83 | 0 |
| Prince of Space (fast, single pass) | 1.66 | 0 |
| Google Java Format (AOSP) | 1.81 | 0 |
| Palantir Java Format (AOSP) | 1.86 | 0 |
| Eclipse JDT (Spotless default) | 1.43 | 0 |

## Tool versions

- Prince of Space: **2.1.3-SNAPSHOT** (Gradle `version` when launched via `./gradlew :formatter-benchmark:run`)
- Google Java Format: 1.27.0 (AOSP)
- Palantir Java Format: 2.71.0 (AOSP)
- Eclipse JDT: **4.34** (Spotless `eclipse()`; same family as `examples/external/outputs/eclipse/`)
- Prettier: 3.4.2 + prettier-plugin-java 2.6.6 (via `npx`)




_Prettier leg skipped (`PRINCE_BENCH_SKIP_PRETTIER=true`)._

Regenerate (after cloning Spring Framework):

```bash
export PRINCE_BENCH_ROOT=/path/to/spring-framework
./gradlew :formatter-benchmark:run
```
