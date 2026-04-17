package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhanced real-world evaluation harness.
 *
 * <p>Set {@code PRINCE_EVAL_ROOTS} to a comma-separated list of Java project checkout paths. The
 * harness formats every {@code .java} file under each root with 9 config permutations (3 width
 * bands × 3 wrap styles), hard-asserts zero parse errors and zero idempotency failures, and writes
 * a Markdown report to {@code PRINCE_EVAL_REPORT_DIR} (default: {@code docs/eval-results/}).
 *
 * <p>Run with: {@code ./gradlew :core:evalTest}
 *
 * <p>Optional: {@code PRINCE_EVAL_CONFIG_NAMES=aggressive-wide,moderate-balanced} runs only those
 * named configs (comma-separated), for faster iteration.
 *
 * <p>Optional: {@code PRINCE_EVAL_MAX_OVER_LONG_SAMPLES} (or alias {@code MAX_OVER_LONG_LINE_SAMPLES})
 * caps how many over-long line records are retained per config for the report sample (default {@code
 * 10000}). Use {@code 0} to keep none. System property {@code prince.eval.maxOverLongSamples} is also
 * honored.
 *
 * <p>Low-memory hosts: set Gradle {@code PRINCE_EVAL_MAX_HEAP} (e.g. {@code 5g}) to size the eval test
 * worker; {@code PRINCE_EVAL_SKIP_SECOND_FORMAT=true} skips the extra {@code format(format(x))} call per
 * file (the engine already converges internally to a fixed point).
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "PRINCE_EVAL_ROOTS", matches = ".+")
class RealWorldEvalTest {

    /** Default cap for retained over-long line samples per config run (totals stay exact). */
    private static final int DEFAULT_MAX_OVER_LONG_LINE_SAMPLES = 10_000;

    // ---------------------------------------------------------------------------
    // Config permutations
    // ---------------------------------------------------------------------------

    record EvalConfig(String name, int preferred, int max, WrapStyle wrapStyle) {

        FormatterConfig toFormatterConfig() {
            return FormatterConfig.builder()
                    .preferredLineLength(preferred)
                    .maxLineLength(max)
                    .wrapStyle(wrapStyle)
                    .build();
        }
    }

    private static final List<EvalConfig> CONFIGS = List.of(
            new EvalConfig("aggressive-wide", 80, 100, WrapStyle.WIDE),
            new EvalConfig("aggressive-balanced", 80, 100, WrapStyle.BALANCED),
            new EvalConfig("aggressive-narrow", 80, 100, WrapStyle.NARROW),
            new EvalConfig("moderate-wide", 100, 120, WrapStyle.WIDE),
            new EvalConfig("moderate-balanced", 100, 120, WrapStyle.BALANCED),
            new EvalConfig("moderate-narrow", 100, 120, WrapStyle.NARROW),
            new EvalConfig("default-wide", 120, 150, WrapStyle.WIDE),
            new EvalConfig("default-balanced", 120, 150, WrapStyle.BALANCED),
            new EvalConfig("default-narrow", 120, 150, WrapStyle.NARROW));

    /** Directory segments that indicate generated / build output — skip their subtrees. */
    private static final Set<String> SKIP_DIRS =
            Set.of("build", ".gradle", ".git", "generated", "generated-sources");

    private static List<EvalConfig> activeConfigs() {
        String raw = System.getenv("PRINCE_EVAL_CONFIG_NAMES");
        if (raw == null || raw.isBlank()) {
            return CONFIGS;
        }
        LinkedHashSet<String> want = new LinkedHashSet<>();
        for (String part : raw.split(",", -1)) {
            String name = part.strip();
            if (!name.isEmpty()) {
                want.add(name);
            }
        }
        List<EvalConfig> picked = CONFIGS.stream().filter(c -> want.contains(c.name())).toList();
        if (picked.isEmpty()) {
            throw new IllegalStateException(
                    "PRINCE_EVAL_CONFIG_NAMES matched no configs. Valid names: "
                            + CONFIGS.stream().map(EvalConfig::name).toList());
        }
        return picked;
    }

    // ---------------------------------------------------------------------------
    // Result types
    // ---------------------------------------------------------------------------

    record OverLongLine(String path, int lineNumber, int length) {}

    record ConfigRunResult(
            EvalConfig config,
            int totalFiles,
            List<String> parseErrors,
            List<String> idempotencyFailures,
            /** Sample of over-long lines (capped); see {@link #formattedOverLongLineCount()}. */
            List<OverLongLine> overLongLines,
            /** Exact count of over-long non-comment lines in formatted output (same rules as samples). */
            int formattedOverLongLineCount,
            int overLongLinesInMain,
            int overLongLinesInTest,
            int overLongLinesInOther,
            Map<String, Integer> overLongKnownOutlierCounts,
            /** Total count of over-long non-comment lines in the original sources (same rules as formatted). */
            int sourceOverLongLineCount,
            /** Files where formatted output had strictly more over-long lines than the source. */
            int filesWhereLongLinesWorsened,
            /** Files where formatted output had strictly fewer over-long lines than the source. */
            int filesWhereLongLinesImproved,
            int reformatted,
            int alreadyClean,
            long elapsedMs) {}

    record ProjectEvalResult(
            String name, String gitHash, List<ConfigRunResult> configResults) {}

    // ---------------------------------------------------------------------------
    // Test entry point
    // ---------------------------------------------------------------------------

    @Test
    void evalRealWorldCodebases() throws IOException {
        String rootsEnv = System.getenv("PRINCE_EVAL_ROOTS");

        List<Path> projects = new ArrayList<>();
        for (String segment : rootsEnv.split(",", -1)) {
            String trimmed = segment.strip();
            if (!trimmed.isEmpty()) {
                projects.add(Path.of(trimmed));
            }
        }

        Path reportDir = resolveReportDir();
        Files.createDirectories(reportDir);

        List<ProjectEvalResult> results = new ArrayList<>();
        List<String> allParseErrors = new ArrayList<>();
        List<String> allIdempotencyFailures = new ArrayList<>();

        for (Path project : projects) {
            assertThat(Files.isDirectory(project))
                    .as("Not a directory: %s", project)
                    .isTrue();

            @Nullable Path fileNamePath = project.getFileName();
            String projectName = fileNamePath != null ? fileNamePath.toString() : project.toString();
            String gitHash = getGitHash(project);
            List<Path> files = collectJavaFiles(project);

            System.out.printf("%nProject: %s (%s) — %d files%n", projectName, gitHash, files.size());

            List<ConfigRunResult> configResults = new ArrayList<>();
            for (EvalConfig evalConfig : activeConfigs()) {
                System.out.printf("  Running config: %s ...%n", evalConfig.name());
                ConfigRunResult result =
                        runConfig(project, evalConfig, files, evalConfig.toFormatterConfig());
                configResults.add(result);

                for (String e : result.parseErrors()) {
                    allParseErrors.add("[" + projectName + " / " + evalConfig.name() + "] " + e);
                }
                for (String e : result.idempotencyFailures()) {
                    allIdempotencyFailures.add(
                            "[" + projectName + " / " + evalConfig.name() + "] " + e);
                }
                int consoleWarnLimit = 50;
                int printed = 0;
                for (OverLongLine w : result.overLongLines()) {
                    if (printed >= consoleWarnLimit) {
                        break;
                    }
                    System.out.printf(
                            "    WARN over-long non-comment line: %s:%d (%d chars)%n",
                            w.path(), w.lineNumber(), w.length());
                    printed++;
                }
                if (result.formattedOverLongLineCount() > printed) {
                    System.out.printf(
                            "    ... %d more over-long lines in formatted output (total %d; see report)%n",
                            result.formattedOverLongLineCount() - printed,
                            result.formattedOverLongLineCount());
                }
                int fmtLong = result.formattedOverLongLineCount();
                int srcLong = result.sourceOverLongLineCount();
                int net = fmtLong - srcLong;
                System.out.printf(
                        "  Done: %d files, %d parse errors, %d idempotency failures,"
                                + " over-long fmt=%d src=%d (net %+d), files worse=%d better=%d, %d ms%n",
                        result.totalFiles(),
                        result.parseErrors().size(),
                        result.idempotencyFailures().size(),
                        fmtLong,
                        srcLong,
                        net,
                        result.filesWhereLongLinesWorsened(),
                        result.filesWhereLongLinesImproved(),
                        result.elapsedMs());
            }
            results.add(new ProjectEvalResult(projectName, gitHash, configResults));
        }

        // Write report before asserting so failures are visible even when the build fails.
        LocalDate reportDate = LocalDate.now(ZoneId.systemDefault());
        String report = new EvalReport(reportDate, resolveFormatterVersion(), results).toMarkdown();
        Path reportFile = reportDir.resolve(reportDate + ".md");
        Files.writeString(reportFile, report);
        System.out.println("\nReport written to: " + reportFile);

        // Hard assertions — collect both lists so a single run surfaces all problems.
        assertThat(allParseErrors)
                .as("Parse errors (must be zero):\n%s", String.join("\n", allParseErrors))
                .isEmpty();
        assertThat(allIdempotencyFailures)
                .as(
                        "Idempotency failures (must be zero):\n%s",
                        String.join("\n", allIdempotencyFailures))
                .isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Per-config run
    // ---------------------------------------------------------------------------

    private ConfigRunResult runConfig(
            Path projectRoot, EvalConfig evalConfig, List<Path> files, FormatterConfig config) {
        Formatter formatter = new Formatter(config);
        List<String> parseErrors = new ArrayList<>();
        List<String> idempotencyFailures = new ArrayList<>();
        List<OverLongLine> overLongLineSamples = new ArrayList<>();
        OverLongAgg overLongAgg = new OverLongAgg();
        Map<String, Integer> knownOutlierCounts = new LinkedHashMap<>();
        int sourceOverLongLineCount = 0;
        int filesWhereLongLinesWorsened = 0;
        int filesWhereLongLinesImproved = 0;
        int reformatted = 0;
        int alreadyClean = 0;
        long startNs = System.nanoTime();
        int maxSamples = maxOverLongLineSamples();
        boolean skipSecondFormat = skipSecondFormatCheck();
        if (skipSecondFormat) {
            System.out.printf(
                    "  Note: PRINCE_EVAL_SKIP_SECOND_FORMAT is set — skipping second format pass per file.%n");
        }

        for (Path file : files) {
            String relative = relativize(projectRoot, file);
            int max = config.maxLineLength();

            final String once;
            final int srcLong;
            final boolean fileAlreadyClean;
            {
                String source;
                try {
                    source = Files.readString(file);
                } catch (IOException e) {
                    parseErrors.add(relative + ": IO error: " + e.getMessage());
                    continue;
                }
                srcLong = countOverLongLines(source, max);
                sourceOverLongLineCount += srcLong;
                try {
                    once = formatter.format(source);
                } catch (FormatterException e) {
                    parseErrors.add(relative + ": " + e.getMessage());
                    continue;
                } catch (RuntimeException e) {
                    parseErrors.add(
                            relative
                                    + ": unexpected formatter error: "
                                    + e.getClass().getSimpleName()
                                    + ": "
                                    + e.getMessage());
                    continue;
                }
                fileAlreadyClean = once.equals(source);
                // Drop `source` before the optional second format so peak heap is ~2× output, not 3×.
            }

            if (!skipSecondFormat) {
                try {
                    String twice = formatter.format(once);
                    if (!twice.equals(once)) {
                        idempotencyFailures.add(relative);
                    }
                } catch (FormatterException e) {
                    idempotencyFailures.add(relative + " (second pass threw): " + e.getMessage());
                } catch (RuntimeException e) {
                    idempotencyFailures.add(
                            relative
                                    + " (second pass unexpected formatter error: "
                                    + e.getClass().getSimpleName()
                                    + ": "
                                    + e.getMessage()
                                    + ")");
                }
            }

            // Over-long line warning: non-comment, non-directive lines only.
            int fmtLongBefore = overLongAgg.total;
            accumulateOverLongLines(
                    relative,
                    once,
                    max,
                    overLongAgg,
                    overLongLineSamples,
                    maxSamples,
                    knownOutlierCounts);
            int fmtLong = overLongAgg.total - fmtLongBefore;

            if (fmtLong > srcLong) {
                filesWhereLongLinesWorsened++;
            } else if (fmtLong < srcLong) {
                filesWhereLongLinesImproved++;
            }

            if (fileAlreadyClean) {
                alreadyClean++;
            } else {
                reformatted++;
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        return new ConfigRunResult(
                evalConfig,
                files.size(),
                parseErrors,
                idempotencyFailures,
                List.copyOf(overLongLineSamples),
                overLongAgg.total,
                overLongAgg.main,
                overLongAgg.test,
                overLongAgg.other,
                Map.copyOf(knownOutlierCounts),
                sourceOverLongLineCount,
                filesWhereLongLinesWorsened,
                filesWhereLongLinesImproved,
                reformatted,
                alreadyClean,
                elapsedMs);
    }

    /** When true, skip {@code format(format(x))} per file (saves a full parse+print per file). */
    private static boolean skipSecondFormatCheck() {
        @Nullable String raw = System.getenv("PRINCE_EVAL_SKIP_SECOND_FORMAT");
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    private static int maxOverLongLineSamples() {
        @Nullable String raw = System.getenv("PRINCE_EVAL_MAX_OVER_LONG_SAMPLES");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("MAX_OVER_LONG_LINE_SAMPLES");
        }
        if (raw != null && !raw.isBlank()) {
            try {
                return Math.max(0, Integer.parseInt(raw.strip()));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid over-long sample cap: " + raw, e);
            }
        }
        @Nullable String prop = System.getProperty("prince.eval.maxOverLongSamples");
        if (prop != null && !prop.isBlank()) {
            return Math.max(0, Integer.parseInt(prop.strip()));
        }
        prop = System.getProperty("MAX_OVER_LONG_LINE_SAMPLES");
        if (prop != null && !prop.isBlank()) {
            return Math.max(0, Integer.parseInt(prop.strip()));
        }
        return DEFAULT_MAX_OVER_LONG_LINE_SAMPLES;
    }

    /** Over-long lines excluding comments and import/package (streaming; avoids {@code String.split}). */
    private static int countOverLongLines(String text, int maxLineLength) {
        int n = 0;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (isOverLongNonCommentNonDirectiveLine(text, lineStart, i, maxLineLength)) {
                    n++;
                }
                lineStart = i + 1;
            }
        }
        return n;
    }

    private static final class OverLongAgg {
        int total;
        int main;
        int test;
        int other;
    }

    private static void accumulateOverLongLines(
            String relativePath,
            String formatted,
            int maxLineLength,
            OverLongAgg agg,
            List<OverLongLine> samples,
            int maxSamples,
            Map<String, Integer> knownOutlierCounts) {
        int lineNumber = 1;
        int lineStart = 0;
        for (int i = 0; i <= formatted.length(); i++) {
            if (i == formatted.length() || formatted.charAt(i) == '\n') {
                if (isOverLongNonCommentNonDirectiveLine(formatted, lineStart, i, maxLineLength)) {
                    agg.total++;
                    if (relativePath.contains("/src/test/")) {
                        agg.test++;
                    } else if (relativePath.contains("/src/main/")) {
                        agg.main++;
                    } else {
                        agg.other++;
                    }
                    if (relativePath.endsWith("/PublicSuffixPatterns.java")) {
                        knownOutlierCounts.merge(relativePath, 1, Integer::sum);
                    }
                    if (samples.size() < maxSamples) {
                        samples.add(new OverLongLine(relativePath, lineNumber, i - lineStart));
                    }
                }
                lineNumber++;
                lineStart = i + 1;
            }
        }
    }

    /**
     * Line = {@code text[lineStart, lineEndExclusive)}. Same rules as the legacy {@code split}-based
     * scan: physical line length (including leading whitespace), excluding comments and import/package.
     */
    private static boolean isOverLongNonCommentNonDirectiveLine(
            String text, int lineStart, int lineEndExclusive, int maxLineLength) {
        int len = lineEndExclusive - lineStart;
        if (len <= maxLineLength) {
            return false;
        }
        int j = lineStart;
        while (j < lineEndExclusive && Character.isWhitespace(text.charAt(j))) {
            j++;
        }
        if (j >= lineEndExclusive) {
            return true;
        }
        if (text.charAt(j) == '/'
                && j + 1 < lineEndExclusive
                && text.charAt(j + 1) == '/') {
            return false;
        }
        if (text.charAt(j) == '*') {
            return false;
        }
        return !regionStartsWith(text, j, lineEndExclusive, "import ")
                && !regionStartsWith(text, j, lineEndExclusive, "package ");
    }

    private static boolean regionStartsWith(String text, int start, int endExclusive, String prefix) {
        int rem = endExclusive - start;
        if (rem < prefix.length()) {
            return false;
        }
        return text.regionMatches(start, prefix, 0, prefix.length());
    }

    // ---------------------------------------------------------------------------
    // File collection
    // ---------------------------------------------------------------------------

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(RealWorldEvalTest::noSkipSegment)
                    .forEach(files::add);
        }
        return files;
    }

    private static boolean noSkipSegment(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            String segment = path.getName(i).toString();
            if (SKIP_DIRS.contains(segment) || isUnsupportedVersionedJavaDir(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUnsupportedVersionedJavaDir(String segment) {
        if (!segment.startsWith("java") || segment.length() <= 4) {
            return false;
        }
        try {
            int level = Integer.parseInt(segment.substring(4));
            // Eval runs with FormatterConfig default language level (JAVA_17).
            return level > 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------------

    private static String relativize(Path root, Path file) {
        try {
            return root.relativize(file).toString();
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }

    private static String getGitHash(Path dir) {
        try {
            Process proc = new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream is = proc.getInputStream()) {
                String output = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
                int exit = proc.waitFor();
                return exit == 0 && !output.isEmpty() ? output : "unknown";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        } catch (IOException e) {
            return "unknown";
        }
    }

    private static Path resolveReportDir() {
        @Nullable String envVal = System.getenv("PRINCE_EVAL_REPORT_DIR");
        if (envVal != null && !envVal.isBlank()) {
            return Path.of(envVal);
        }
        // Walk up from cwd to find the repo root (contains settings.gradle.kts).
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (true) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate.resolve("docs/eval-results");
            }
            @Nullable Path parent = candidate.getParent();
            if (parent == null) {
                break;
            }
            candidate = parent;
        }
        return Path.of("docs/eval-results");
    }

    private static String resolveFormatterVersion() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (true) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                Path versionFile = candidate.resolve("modules/core/build/resources/main/version.txt");
                if (!Files.exists(versionFile)) {
                    Path legacy = candidate.resolve("core/build/resources/main/version.txt");
                    if (Files.exists(legacy)) {
                        versionFile = legacy;
                    }
                }
                if (Files.exists(versionFile)) {
                    try {
                        String value = Files.readString(versionFile).strip();
                        if (!value.isEmpty()) {
                            return value;
                        }
                    } catch (IOException ignored) {
                        return "dev";
                    }
                }
                return "dev";
            }
            @Nullable Path parent = candidate.getParent();
            if (parent == null) {
                return "dev";
            }
            candidate = parent;
        }
    }
}
