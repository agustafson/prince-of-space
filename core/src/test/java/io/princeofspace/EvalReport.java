package io.princeofspace;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

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
                sb.append("- Over-long non-comment lines: ").append(run.overLongLines().size()).append('\n');
                sb.append("- Time: ").append(formatSeconds(run.elapsedMs()))
                        .append(" (").append(formatOneDecimal(avgMs)).append(" ms/file avg)\n\n");

                if (!run.overLongLines().isEmpty()) {
                    sb.append("<details><summary>Over-long line warnings</summary>\n\n");
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
        sb.append("| Project | Config | Parse errors | Idempotency failures | Over-long lines |\n");
        sb.append("|---------|--------|-------------|----------------------|-----------------|\n");
        for (RealWorldEvalTest.ProjectEvalResult project : results) {
            for (RealWorldEvalTest.ConfigRunResult run : project.configResults()) {
                sb.append("| ").append(project.name())
                        .append(" | ").append(run.config().name())
                        .append(" | ").append(run.parseErrors().size())
                        .append(" | ").append(run.idempotencyFailures().size())
                        .append(" | ").append(run.overLongLines().size())
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
}
