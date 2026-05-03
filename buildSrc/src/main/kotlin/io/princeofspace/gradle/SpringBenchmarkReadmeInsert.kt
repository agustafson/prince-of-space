package io.princeofspace.gradle

/** Markdown fragment for README.md between the perf sync markers; used by the root `syncReadmePerfSection` task. */
object SpringBenchmarkReadmeInsert {
    @JvmStatic
    fun fromReport(report: String): String {
        val lines = report.lines()
        fun req(predicate: (String) -> Boolean): String =
            lines.firstOrNull(predicate)
                ?: error("Unexpected perf report shape — missing line in docs/perf-results/spring-framework.md")

        val date = req { it.startsWith("Date:") }.removePrefix("Date:").trim()
        val gitLine = req { it.startsWith("Git revision:") }
        val hash =
            gitLine.substringAfter('`').substringBefore('`').ifEmpty {
                error("Could not parse git revision from perf report")
            }
        val filesLine = req { it.startsWith("Files formatted:") }
        val filesBit = filesLine.removePrefix("Files formatted:").trim()
        val jvmLine = req { it.startsWith("JVM:") }
        val configLine = req { it.startsWith("Prince of Space uses") }

        val idxResults = lines.indexOf("## Results")
        val idxTool = lines.indexOf("## Tool versions")
        require(idxResults >= 0 && idxTool > idxResults) { "Perf report missing ## Results / ## Tool versions" }
        val tableMd = lines.subList(idxResults, idxTool).joinToString("\n").trim()

        val prettierNote =
            if (report.contains("Prettier leg skipped")) {
                "_Prettier throughput leg skipped in this run (`PRINCE_BENCH_SKIP_PRETTIER=true`)._"
            } else {
                "Optional Prettier runs via `npx` and **rewrites** sources — use a disposable clone."
            }

        return sequenceOf(
            "_Auto-generated from [`docs/perf-results/spring-framework.md`](docs/perf-results/spring-framework.md) — do not edit between markers; run `./gradlew refreshSpringBenchmarkReadme` or CI._",
            "",
            "Wall-clock run on the [Spring Framework](https://github.com/spring-projects/spring-framework) corpus (**$hash**, $filesBit files, same path filters as the external eval harness). Each JVM formatter runs in one process with warmup; $jvmLine",
            "",
            configLine,
            "",
            tableMd,
            "",
            "Full detail and regeneration: `./gradlew :formatter-benchmark:run` with `PRINCE_BENCH_ROOT` pointing at a checkout. $prettierNote Eclipse JDT is not in this JVM harness (Spotless bootstraps it via Equo); see [`examples/external/outputs/eclipse/`](examples/external/outputs/eclipse/) for showroom output.",
            "",
            "_Report date: **$date**._",
        ).joinToString("\n")
    }
}
