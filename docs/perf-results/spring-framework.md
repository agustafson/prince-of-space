# Formatter throughput — Spring Framework corpus

Date: 2026-11-01
Corpus root (local): `/home/runner/work/prince-of-space/prince-of-space/spring-framework-bench`
Git revision: `ed4e0f8`
Files formatted: **9265**
JVM: **21.0.13+9-LTS**
JVM formatter workers (Prince / Google / Palantir / Eclipse): **1**

Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Google Java Format and Palantir Java Format use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons. Eclipse JDT uses Spotless `eclipse()` defaults (`examples/external/outputs/eclipse/`).

## Results

| Formatter | Avg ms / file | Failures |
|-----------|-----------------|----------|
| Prince of Space (strict, fixed-point) | 6.51 | 0 |
| Prince of Space (fast, single pass) | 2.63 | 0 |
| Google Java Format (AOSP) | 2.92 | 0 |
| Palantir Java Format (AOSP) | 2.85 | 0 |
| Eclipse JDT (Spotless default) | 2.34 | 0 |

## Tool versions

- Prince of Space: **2.2.1-SNAPSHOT** (Gradle `version` when launched via `./gradlew :formatter-benchmark:run`)
- Google Java Format: 1.35.0 (AOSP)
- Palantir Java Format: 2.103.0 (AOSP)
- Eclipse JDT: **4.34** (Spotless `eclipse()`; same family as `examples/external/outputs/eclipse/`)
- Prettier: 3.4.2 + prettier-plugin-java 2.6.6 (via `npx`)




_Prettier leg skipped (`PRINCE_BENCH_SKIP_PRETTIER=true`)._

Regenerate (after cloning Spring Framework):

```bash
export PRINCE_BENCH_ROOT=/path/to/spring-framework
./gradlew :formatter-benchmark:run
```
