package io.princeofspace;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record EvalReport(
        LocalDate date,
        String formatterVersion,
        List<RealWorldEvalTest.ProjectEvalResult> results) {

    String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Prince of Space — Eval Report\n");
        sb.append("Date: ").append(date).append('\n');
        sb.append("Formatter version: ").append(formatterVersion).append("\n\n");

        for (RealWorldEvalTest.ProjectEvalResult project : results) {
            sb.append("## ").append(project.name()).append(" @ ").append(project.gitHash()).append("\n\n");
            for (RealWorldEvalTest.ConfigRunResult run : project.configResults()) {
                RealWorldEvalTest.EvalConfig config = run.config();
                int total = run.totalFiles();
                int attempted = total;
                double reformattedPct = percent(run.reformatted(), attempted);
                double cleanPct = percent(run.alreadyClean(), attempted);
                double avgMs = attempted == 0 ? 0.0 : (double) run.elapsedMs() / attempted;

                sb.append("### ").append(config.name())
                        .append(" (preferred=").append(config.preferred())
                        .append(", max=").append(config.max())
                        .append(", wrapStyle=").append(config.wrapStyle())
                        .append(")\n");
                sb.append("- Files: ").append(total).append(" scanned, ").append(attempted).append(" attempted\n");
                sb.append("- Parse errors: ").append(run.parseErrors().size()).append('\n');
                sb.append("- Idempotency failures: ").append(run.idempotencyFailures().size()).append('\n');
                sb.append("- Reformatted: ").append(run.reformatted())
                        .append(" (").append(formatPercent(reformattedPct)).append(")\n");
                sb.append("- Already clean: ").append(run.alreadyClean())
                        .append(" (").append(formatPercent(cleanPct)).append(")\n");
                OverLongBuckets buckets = bucketOverLongLines(run);
                sb.append("- Over-long non-comment lines: ").append(run.formattedOverLongLineCount())
                        .append(" (main=").append(buckets.main())
                        .append(", test=").append(buckets.test())
                        .append(", other=").append(buckets.other())
                        .append(")\n");
                if (buckets.knownOutlierWarnings() > 0) {
                    sb.append("- Known outlier warnings: ").append(buckets.knownOutlierWarnings())
                            .append(" across ").append(buckets.knownOutlierFiles().size())
                            .append(" file(s): ")
                            .append(String.join(", ", buckets.knownOutlierFiles()))
                            .append('\n');
                }
                int fmtLong = run.formattedOverLongLineCount();
                int srcLong = run.sourceOverLongLineCount();
                int net = fmtLong - srcLong;
                sb.append("- Over-long lines vs source: formatted=").append(fmtLong)
                        .append(", source=").append(srcLong)
                        .append(", net change (formatted−source)=")
                        .append(net >= 0 ? "+" : "")
                        .append(net)
                        .append('\n');
                sb.append("- Files with more over-long lines after format: ")
                        .append(run.filesWhereLongLinesWorsened())
                        .append("; fewer: ")
                        .append(run.filesWhereLongLinesImproved())
                        .append('\n');
                sb.append("- Time: ").append(formatSeconds(run.elapsedMs()))
                        .append(" (").append(formatOneDecimal(avgMs)).append(" ms/file avg)\n\n");

                if (!run.overLongLines().isEmpty()) {
                    sb.append("<details><summary>Over-long line warnings (sample)</summary>\n\n");
                    if (run.formattedOverLongLineCount() > run.overLongLines().size()) {
                        sb.append("(Showing ")
                                .append(run.overLongLines().size())
                                .append(" of ")
                                .append(run.formattedOverLongLineCount())
                                .append(" lines.)\n\n");
                    }
                    for (RealWorldEvalTest.OverLongLine warning : run.overLongLines()) {
                        sb.append("- `")
                                .append(project.name())
                                .append('/')
                                .append(warning.path())
                                .append(':')
                                .append(warning.lineNumber())
                                .append("` — ")
                                .append(warning.length())
                                .append(" chars\n");
                    }
                    sb.append("\n</details>\n\n");
                }
            }
        }

        sb.append("## Summary\n\n");
        sb.append("| Project | Config | Parse errors | Idempotency failures | Over-long (fmt) | Over-long (src) | Net Δ | Files worse | Files better |\n");
        sb.append("|---------|--------|-------------|----------------------|----------------|-----------------|-------|------------|-------------|\n");
        for (RealWorldEvalTest.ProjectEvalResult project : results) {
            for (RealWorldEvalTest.ConfigRunResult run : project.configResults()) {
                int fmtLong = run.formattedOverLongLineCount();
                int srcLong = run.sourceOverLongLineCount();
                int net = fmtLong - srcLong;
                sb.append("| ").append(project.name())
                        .append(" | ").append(run.config().name())
                        .append(" | ").append(run.parseErrors().size())
                        .append(" | ").append(run.idempotencyFailures().size())
                        .append(" | ").append(fmtLong)
                        .append(" | ").append(srcLong)
                        .append(" | ").append(net >= 0 ? "+" : "").append(net)
                        .append(" | ").append(run.filesWhereLongLinesWorsened())
                        .append(" | ").append(run.filesWhereLongLinesImproved())
                        .append(" |\n");
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    private static double percent(int value, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (100.0 * value) / total;
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    private static String formatSeconds(long elapsedMs) {
        return formatOneDecimal(elapsedMs / 1000.0) + " s";
    }

    private static String formatOneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static OverLongBuckets bucketOverLongLines(RealWorldEvalTest.ConfigRunResult run) {
        Map<String, Integer> knownOutlierCounts = run.overLongKnownOutlierCounts();
        List<String> knownOutlierFiles = knownOutlierCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
        int knownOutlierWarnings = knownOutlierCounts.values().stream().mapToInt(Integer::intValue).sum();
        return new OverLongBuckets(
                run.overLongLinesInMain(),
                run.overLongLinesInTest(),
                run.overLongLinesInOther(),
                knownOutlierWarnings,
                knownOutlierFiles);
    }

    private record OverLongBuckets(
            int main, int test, int other, int knownOutlierWarnings, List<String> knownOutlierFiles) {}
}
