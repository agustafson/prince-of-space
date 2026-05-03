package io.princeofspace.benchmark;

import io.princeofspace.BenchDiagnostics;
import io.princeofspace.Formatter;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Formats every {@code .java} file under {@code PRINCE_BENCH_ROOT} with Prince of Space and other
 * JVM formatters, optionally Prettier, and writes a Markdown report.
 *
 * <p>Environment:
 *
 * <ul>
 *   <li>{@code PRINCE_BENCH_ROOT} — required checkout root (e.g. Spring Framework).
 *   <li>{@code PRINCE_BENCH_MAX_FILES} — optional cap for quick smoke runs.
 *   <li>{@code PRINCE_BENCH_REPORT} — optional report path (default: {@code
 *       docs/perf-results/spring-framework.md} under the repo root).
 *   <li>{@code PRINCE_BENCH_SKIP_PRETTIER} — set to {@code true} to skip the Prettier ({@code npx})
 *       leg (Unix/macOS; requires network on first {@code npx} run).
 *   <li>{@code PRINCE_BENCH_WARMUP} — set to {@code false} to skip in-process warmup passes.
 *   <li>{@code PRINCE_BENCH_WORKERS} — optional parallel file workers (default {@code 1}) for JVM
 *       formatter legs; same pool size is used for Prince, Google, and Palantir.
 *   <li>{@code PRINCE_BENCH_DIAGNOSTICS} — set to {@code true} to enable {@code
 *       -Dprince.bench.diagnostics=true} and emit a phase-timing section for the strict Prince leg
 *       only.
 * </ul>
 */
public final class FormatterBenchmark {

    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final long MILLISECONDS_PER_SECOND = 1_000L;
    private static final int WARMUP_ITERATIONS = 2;

    /**
     * Mirrors {@link io.princeofspace.RealWorldEvalTest}: directory segments {@code javaNN} above the
     * default formatter language level are skipped.
     */
    private static final String JAVA_LANGUAGE_FOLDER_PREFIX = "java";

    private static final int DEFAULT_FORMATTER_LANGUAGE_LEVEL = 17;

    private static final Set<String> SKIP_DIRS =
            Set.of("build", ".gradle", ".git", "generated", "generated-sources");

    /** Keep aligned with {@code gradle/libs.versions.toml} ({@code npm-prettier}). */
    private static final String NPM_PRETTIER = "3.4.2";

    /** Keep aligned with {@code gradle/libs.versions.toml} ({@code npm-prettier-plugin-java}). */
    private static final String NPM_PRETTIER_PLUGIN_JAVA = "2.6.6";

    private FormatterBenchmark() {}

    public static void main(String[] args) throws Exception {
        String rootEnv = System.getenv("PRINCE_BENCH_ROOT");
        if (rootEnv == null || rootEnv.isBlank()) {
            throw new IllegalStateException("Set PRINCE_BENCH_ROOT to a Java project checkout.");
        }
        Path root = Path.of(rootEnv.strip());
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Not a directory: " + root);
        }

        int maxFiles = Integer.MAX_VALUE;
        String maxRaw = System.getenv("PRINCE_BENCH_MAX_FILES");
        if (maxRaw != null && !maxRaw.isBlank()) {
            maxFiles = Integer.parseInt(maxRaw.strip());
        }

        boolean skipPrettier = truthy(System.getenv("PRINCE_BENCH_SKIP_PRETTIER"));
        boolean warmup = !"false".equalsIgnoreCase(System.getenv("PRINCE_BENCH_WARMUP"));
        boolean benchDiagnostics = truthy(System.getenv("PRINCE_BENCH_DIAGNOSTICS"));
        int workers = parseWorkerCount();

        List<Path> files = collectJavaFiles(root);
        files.sort(Comparator.comparing(Path::toString));
        if (files.size() > maxFiles) {
            files = new ArrayList<>(files.subList(0, maxFiles));
        }

        String gitHash = gitShortHash(root);
        Path reportPath = resolveReportPath();

        Formatter princeStrict = new Formatter(FormatterConfig.defaults(), false);
        Formatter princeFast = new Formatter(FormatterConfig.defaults(), true);
        com.google.googlejavaformat.java.Formatter googleFmt =
                new com.google.googlejavaformat.java.Formatter(
                        com.google.googlejavaformat.java.JavaFormatterOptions.builder()
                                .style(com.google.googlejavaformat.java.JavaFormatterOptions.Style.AOSP)
                                .build());
        com.palantir.javaformat.java.Formatter palantirFmt =
                com.palantir.javaformat.java.Formatter.createFormatter(
                        com.palantir.javaformat.java.JavaFormatterOptions.builder()
                                .style(com.palantir.javaformat.java.JavaFormatterOptions.Style.AOSP)
                                .build());

        List<BenchRow> rows = new ArrayList<>();

        String diagMarkdown = "";
        if (benchDiagnostics) {
            System.setProperty("prince.bench.diagnostics", "true");
            BenchDiagnostics.reset();
        }

        rows.add(
                runJvm(
                        "Prince of Space (strict, fixed-point)",
                        princeStrict,
                        princeFast,
                        googleFmt,
                        palantirFmt,
                        root,
                        files,
                        Engine.PRINCE_STRICT,
                        warmup,
                        workers));

        if (benchDiagnostics) {
            diagMarkdown = BenchDiagnostics.toMarkdownSection().strip();
            System.setProperty("prince.bench.diagnostics", "false");
        }

        rows.add(
                runJvm(
                        "Prince of Space (fast, single pass)",
                        princeStrict,
                        princeFast,
                        googleFmt,
                        palantirFmt,
                        root,
                        files,
                        Engine.PRINCE_FAST,
                        warmup,
                        workers));

        rows.add(
                runJvm(
                        "Google Java Format (AOSP)",
                        princeStrict,
                        princeFast,
                        googleFmt,
                        palantirFmt,
                        root,
                        files,
                        Engine.GOOGLE,
                        warmup,
                        workers));
        rows.add(
                runJvm(
                        "Palantir Java Format (AOSP)",
                        princeStrict,
                        princeFast,
                        googleFmt,
                        palantirFmt,
                        root,
                        files,
                        Engine.PALANTIR,
                        warmup,
                        workers));

        if (!skipPrettier) {
            rows.add(runPrettier(root, files));
        }

        writeReport(reportPath, root, gitHash, files.size(), rows, skipPrettier, diagMarkdown, workers);
        System.out.printf("%nWrote %s%n", reportPath.toAbsolutePath());
    }

    private enum Engine {
        PRINCE_STRICT,
        PRINCE_FAST,
        GOOGLE,
        PALANTIR
    }

    private static int parseWorkerCount() {
        String raw = System.getenv("PRINCE_BENCH_WORKERS");
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int n = Integer.parseInt(raw.strip());
        return Math.max(1, n);
    }

    @SuppressWarnings({"PMD.EmptyCatchBlock", "PMD.CloseResource"})
    private static BenchRow runJvm(
            String label,
            Formatter princeStrict,
            Formatter princeFast,
            com.google.googlejavaformat.java.Formatter googleFmt,
            com.palantir.javaformat.java.Formatter palantirFmt,
            Path root,
            List<Path> files,
            Engine engine,
            boolean warmup,
            int workers)
            throws IOException, InterruptedException {
        if (warmup && !files.isEmpty()) {
            Path p = files.get(0);
            String sample = Files.readString(p, StandardCharsets.UTF_8);
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                try {
                    formatWith(engine, princeStrict, princeFast, googleFmt, palantirFmt, sample, p);
                } catch (com.google.googlejavaformat.java.FormatterException
                        | com.palantir.javaformat.java.FormatterException e) {
                    // Warmup best-effort only — the first file may not suit every engine.
                }
            }
        }

        AtomicLong failures = new AtomicLong();
        long t0 = System.nanoTime();
        if (workers <= 1 || files.isEmpty()) {
            for (Path file : files) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                try {
                    formatWith(engine, princeStrict, princeFast, googleFmt, palantirFmt, src, file);
                } catch (com.google.googlejavaformat.java.FormatterException
                        | com.palantir.javaformat.java.FormatterException
                        | RuntimeException e) {
                    failures.incrementAndGet();
                    System.err.printf("%s — %s: %s%n", label, root.relativize(file), e.getMessage());
                }
            }
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            try {
                List<Future<?>> futures = new ArrayList<>(files.size());
                for (Path file : files) {
                    futures.add(
                            pool.submit(
                                    () -> {
                                        try {
                                            String src =
                                                    Files.readString(file, StandardCharsets.UTF_8);
                                            formatWith(
                                                    engine,
                                                    princeStrict,
                                                    princeFast,
                                                    googleFmt,
                                                    palantirFmt,
                                                    src,
                                                    file);
                                        } catch (com.google.googlejavaformat.java.FormatterException
                                                | com.palantir.javaformat.java.FormatterException
                                                | RuntimeException e) {
                                            failures.incrementAndGet();
                                            System.err.printf(
                                                    "%s — %s: %s%n",
                                                    label, root.relativize(file), e.getMessage());
                                        } catch (IOException e) {
                                            failures.incrementAndGet();
                                            System.err.printf(
                                                    "%s — %s: %s%n",
                                                    label, root.relativize(file), e.getMessage());
                                        }
                                    }));
                }
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException e) {
                        failures.incrementAndGet();
                        Throwable cause = e.getCause();
                        String msg = cause != null ? cause.getMessage() : e.getMessage();
                        System.err.printf("%s: %s%n", label, msg);
                    }
                }
            } finally {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(1, TimeUnit.HOURS)) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / NANOSECONDS_PER_MILLISECOND;
        double msPerFile = files.isEmpty() ? 0.0 : (double) elapsedMs / files.size();
        return new BenchRow(label, elapsedMs, msPerFile, failures.get());
    }

    private static String formatWith(
            Engine engine,
            Formatter princeStrict,
            Formatter princeFast,
            com.google.googlejavaformat.java.Formatter googleFmt,
            com.palantir.javaformat.java.Formatter palantirFmt,
            String src,
            Path fileForDiag)
            throws com.google.googlejavaformat.java.FormatterException,
                    com.palantir.javaformat.java.FormatterException {
        return switch (engine) {
            case PRINCE_STRICT -> {
                FormatResult r = princeStrict.formatResult(src, fileForDiag);
                if (r instanceof FormatResult.Success s) {
                    yield s.formattedSource();
                }
                throw new IllegalStateException(r.toString());
            }
            case PRINCE_FAST -> {
                FormatResult r = princeFast.formatResult(src, fileForDiag);
                if (r instanceof FormatResult.Success s) {
                    yield s.formattedSource();
                }
                throw new IllegalStateException(r.toString());
            }
            case GOOGLE -> googleFmt.formatSource(src);
            case PALANTIR -> palantirFmt.formatSource(src);
        };
    }

    private static BenchRow runPrettier(Path root, List<Path> files) throws IOException, InterruptedException {
        Path ignore = Files.createTempFile("prince-bench-prettier", ".ignore");
        String ignoreBody =
                """
                **/build/**
                **/.gradle/**
                **/.git/**
                **/generated/**
                **/generated-sources/**
                """;
        Files.writeString(ignore, ignoreBody, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        Path listFile = Files.createTempFile("prince-bench-java-files", ".txt");
        List<String> rel = new ArrayList<>(files.size());
        for (Path f : files) {
            rel.add(root.relativize(f).toString().replace('\\', '/'));
        }
        Files.write(listFile, rel, StandardCharsets.UTF_8);

        String cmd =
                "cd \""
                        + root.toAbsolutePath()
                        + "\" && xargs -n 200 npx --yes -p prettier@"
                        + NPM_PRETTIER
                        + " -p prettier-plugin-java@"
                        + NPM_PRETTIER_PLUGIN_JAVA
                        + " prettier --write --ignore-path \""
                        + ignore.toAbsolutePath()
                        + "\" < \""
                        + listFile.toAbsolutePath()
                        + "\"";

        long t0 = System.nanoTime();
        Process proc = new ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream is = proc.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = proc.waitFor();
        long elapsedMs = (System.nanoTime() - t0) / NANOSECONDS_PER_MILLISECOND;
        Files.deleteIfExists(ignore);
        Files.deleteIfExists(listFile);

        if (code != 0) {
            System.err.println(output);
            throw new IllegalStateException("Prettier exited with " + code);
        }

        double msPerFile = files.isEmpty() ? 0.0 : (double) elapsedMs / files.size();
        return new BenchRow(
                "Prettier + prettier-plugin-java (batch via npx)", elapsedMs, msPerFile, 0);
    }

    private record BenchRow(String name, long elapsedMs, double msPerFile, long failures) {}

    private static void writeReport(
            Path reportPath,
            Path corpusRoot,
            String gitHash,
            int fileCount,
            List<BenchRow> rows,
            boolean prettierSkipped,
            String benchDiagnosticsMarkdown,
            int workers)
            throws IOException {
        Files.createDirectories(reportPath.getParent());

        String versions =
                String.format(
                        Locale.ROOT,
                        "- Prince of Space: **%s** (Gradle `version` when launched via `./gradlew"
                                + " :formatter-benchmark:run`)%n"
                                + "- Google Java Format: %s (AOSP)%n"
                                + "- Palantir Java Format: %s (AOSP)%n"
                                + "- Prettier: %s + prettier-plugin-java %s (via `npx`)%n",
                        princeFormatterVersion(),
                        googleJavaFormatJarVersion(),
                        palantirJarVersion(),
                        NPM_PRETTIER,
                        NPM_PRETTIER_PLUGIN_JAVA);

        String fmt =
                """
                # Formatter throughput — Spring Framework corpus

                Date: %s
                Corpus root (local): `%s`
                Git revision: `%s`
                Files formatted: **%d**
                JVM: **%s**
                JVM formatter workers (Prince / Google / Palantir): **%d**

                Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Other JVM formatters use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons.

                ## Results

                | Formatter | Wall time | ms / file | Failures |
                |-----------|-----------|-----------|----------|
                %s

                ## Tool versions

                %s

                %s

                %s

                Regenerate (after cloning Spring Framework):

                ```bash
                export PRINCE_BENCH_ROOT=/path/to/spring-framework
                ./gradlew :formatter-benchmark:run
                ```
                """
                        .stripIndent();

        StringBuilder tableRows = new StringBuilder();
        for (BenchRow row : rows) {
            tableRows.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %d s | %.2f | %d |%n",
                            row.name(),
                            row.elapsedMs() / MILLISECONDS_PER_SECOND,
                            row.msPerFile(),
                            row.failures()));
        }

        String prettierNote =
                prettierSkipped
                        ? "_Prettier leg skipped (`PRINCE_BENCH_SKIP_PRETTIER=true`)._"
                        : "";

        String diagBlock = benchDiagnosticsMarkdown.isBlank() ? "" : benchDiagnosticsMarkdown + "\n";

        String body =
                String.format(
                        Locale.ROOT,
                        fmt,
                        LocalDate.now(ZoneId.systemDefault()),
                        corpusRoot.toAbsolutePath(),
                        gitHash,
                        fileCount,
                        Runtime.version(),
                        workers,
                        tableRows.toString().stripTrailing(),
                        versions,
                        diagBlock,
                        prettierNote);

        Files.writeString(reportPath, body, StandardCharsets.UTF_8);
    }

    private static String princeFormatterVersion() {
        String p = System.getProperty("PRINCE_BENCH_FORMATTER_VERSION");
        return p != null && !p.isBlank() ? p : "unknown (set PRINCE_BENCH_FORMATTER_VERSION or use Gradle run)";
    }

    private static String googleJavaFormatJarVersion() {
        Package p = com.google.googlejavaformat.java.Formatter.class.getPackage();
        String v = p != null ? p.getImplementationVersion() : null;
        return v != null ? v : "(unknown)";
    }

    private static String palantirJarVersion() {
        Package p = com.palantir.javaformat.java.Formatter.class.getPackage();
        String v = p != null ? p.getImplementationVersion() : null;
        return v != null ? v : "(unknown)";
    }

    private static Path resolveReportPath() {
        String env = System.getenv("PRINCE_BENCH_REPORT");
        if (env != null && !env.isBlank()) {
            return Path.of(env.strip());
        }
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (true) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate.resolve("docs/perf-results/spring-framework.md");
            }
            Path parent = candidate.getParent();
            if (parent == null) {
                break;
            }
            candidate = parent;
        }
        return Path.of("docs/perf-results/spring-framework.md");
    }

    private static String gitShortHash(Path dir) {
        try {
            Process proc =
                    new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "--short", "HEAD")
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

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(FormatterBenchmark::noSkipSegment)
                    .forEach(files::add);
        }
        return files;
    }

    static boolean noSkipSegment(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            String segment = path.getName(i).toString();
            if (SKIP_DIRS.contains(segment) || isUnsupportedVersionedJavaDir(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mirrors {@code RealWorldEvalTest}: skip {@code java21}/{@code java25} style dirs when using
     * default Java-17 formatter settings.
     */
    private static boolean isUnsupportedVersionedJavaDir(String segment) {
        if (!segment.startsWith(JAVA_LANGUAGE_FOLDER_PREFIX)
                || segment.length() <= JAVA_LANGUAGE_FOLDER_PREFIX.length()) {
            return false;
        }
        try {
            int level = Integer.parseInt(segment.substring(JAVA_LANGUAGE_FOLDER_PREFIX.length()));
            return level > DEFAULT_FORMATTER_LANGUAGE_LEVEL;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean truthy(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.strip().toLowerCase(Locale.ROOT);
        return s.equals("1") || s.equals("true") || s.equals("yes");
    }
}
