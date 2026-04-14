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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "PRINCE_EVAL_ROOTS", matches = ".+")
class RealWorldEvalTest {

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
            List<OverLongLine> overLongLines,
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
                for (OverLongLine w : result.overLongLines()) {
                    System.out.printf(
                            "    WARN over-long non-comment line: %s:%d (%d chars)%n",
                            w.path(), w.lineNumber(), w.length());
                }
                System.out.printf(
                        "  Done: %d files, %d parse errors, %d idempotency failures,"
                                + " %d over-long, %d ms%n",
                        result.totalFiles(),
                        result.parseErrors().size(),
                        result.idempotencyFailures().size(),
                        result.overLongLines().size(),
                        result.elapsedMs());
            }
            results.add(new ProjectEvalResult(projectName, gitHash, configResults));
        }

        // Write report before asserting so failures are visible even when the build fails.
        String report = new EvalReport(LocalDate.now(), resolveFormatterVersion(), results).toMarkdown();
        Path reportFile = reportDir.resolve(LocalDate.now() + ".md");
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
        List<OverLongLine> overLongLines = new ArrayList<>();
        int reformatted = 0;
        int alreadyClean = 0;
        long startNs = System.nanoTime();

        for (Path file : files) {
            String source;
            try {
                source = Files.readString(file);
            } catch (IOException e) {
                parseErrors.add(relativize(projectRoot, file) + ": IO error: " + e.getMessage());
                continue;
            }

            String once;
            try {
                once = formatter.format(source);
            } catch (FormatterException e) {
                parseErrors.add(relativize(projectRoot, file) + ": " + e.getMessage());
                continue;
            } catch (RuntimeException e) {
                parseErrors.add(
                        relativize(projectRoot, file)
                                + ": unexpected formatter error: "
                                + e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage());
                continue;
            }

            // Idempotency: format the already-formatted output a second time.
            try {
                String twice = formatter.format(once);
                if (!twice.equals(once)) {
                    idempotencyFailures.add(relativize(projectRoot, file));
                }
            } catch (FormatterException e) {
                idempotencyFailures.add(
                        relativize(projectRoot, file) + " (second pass threw): " + e.getMessage());
            } catch (RuntimeException e) {
                idempotencyFailures.add(
                        relativize(projectRoot, file)
                                + " (second pass unexpected formatter error: "
                                + e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage()
                                + ")");
            }

            // Over-long line warning: non-comment, non-directive lines only.
            checkOverLongLines(
                    relativize(projectRoot, file), once, config.maxLineLength(), overLongLines);

            if (once.equals(source)) {
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
                overLongLines,
                reformatted,
                alreadyClean,
                elapsedMs);
    }

    private static void checkOverLongLines(
            String relativePath, String formatted, int maxLineLength, List<OverLongLine> sink) {
        String[] lines = formatted.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() <= maxLineLength) {
                continue;
            }
            String trimmed = line.stripLeading();
            boolean isComment = trimmed.startsWith("//") || trimmed.startsWith("*");
            boolean isDirective =
                    trimmed.startsWith("import ") || trimmed.startsWith("package ");
            if (!isComment && !isDirective) {
                sink.add(new OverLongLine(relativePath, i + 1, line.length()));
            }
        }
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
                Path versionFile = candidate.resolve("core/build/resources/main/version.txt");
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
