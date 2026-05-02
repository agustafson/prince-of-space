package io.princeofspace.cli;

import io.princeofspace.Formatter;
import io.princeofspace.FormatterException;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.util.Objects.requireNonNullElse;

/**
 * Command-line entry for formatting Java sources. See {@code --help} for options.
 *
 * <p><b>Exit codes:</b> {@code 0} success; {@code 1} {@code --check} found files needing format; {@code 2}
 * user error, parse failure, I/O, or other format failure; {@code 3} non-convergent format (likely formatter
 * defect — see {@link FormatResult.NonConvergent}).
 *
 * <p>Picocli assigns {@link CommandLine.Option}-annotated fields via reflection before {@link #call()};
 * IntelliJ otherwise reports false positives (unused, local-variable, or final-field suggestions).
 */
@SuppressWarnings({
    "FieldCanBeLocal",
    "CanBeFinal",
    "unused"
})
@CommandLine.Command(
        name = "prince-of-space",
        mixinStandardHelpOptions = true,
        version = "prince-of-space-cli",
        description = "Format Java source using the Prince of Space formatter.")
public final class Main implements Callable<Integer> {
    private static final String JAVA_FILE_SUFFIX = ".java";
    /** Serialize stderr lines from verbose batch processing vs. error reporting. */
    private static final Object STDERR_LOCK = new Object();

    /** Exit when formatting fails to converge (likely formatter bug); distinct from parse/config failures ({@code 2}). */
    private static final int EXIT_NON_CONVERGENT = 3;

    @CommandLine.Option(
            names = "--check",
            description =
                    "Check only; exit 1 if any file would change (does not write files). Each matched file is still "
                            + "read and parsed like a format run — there is no cheap metadata-only pass. Use "
                            + "--fail-fast with --check to stop after the first file that would change.")
    private boolean check;

    @CommandLine.Option(
            names = "--fail-fast",
            description =
                    "Only meaningful with --check: stop after the first file that would need formatting instead of "
                            + "scanning every matched file.")
    private boolean failFast;

    @CommandLine.Option(
            names = "--java-version",
            description =
                    "Java language level for parsing (8+ maps to JavaParser LanguageLevel.JAVA_N; "
                            + "newer releases work when the bundled JavaParser defines the enum constant)")
    private int javaVersion = FormatterConfig.defaultJavaVersion();

    @CommandLine.Option(names = "--indent-style", description = "Indent style: SPACES or TABS.", defaultValue = "SPACES")
    private IndentStyle indentStyle = IndentStyle.SPACES;

    @CommandLine.Option(
            names = "--indent-size",
            description = "Indent units per block level.",
            defaultValue = "4") // must match FormatterConfig.DEFAULT_INDENT_SIZE (annotation value must be constant)
    private int indentSize = FormatterConfig.DEFAULT_INDENT_SIZE;

    @CommandLine.Option(
            names = "--line-length",
            description = "Target line length for wrapping.",
            defaultValue = "120") // must match FormatterConfig.DEFAULT_LINE_LENGTH (annotation value must be constant)
    private int lineLength = FormatterConfig.DEFAULT_LINE_LENGTH;

    @CommandLine.Option(names = "--wrap-style", description = "Wrap style: NARROW, BALANCED, or WIDE.", defaultValue = "BALANCED")
    private WrapStyle wrapStyle = WrapStyle.BALANCED;

    @CommandLine.Option(
            names = "--closing-paren-on-new-line",
            negatable = true,
            description = "Place closing ')' on its own line when argument lists wrap (default: true).")
    private boolean closingParenOnNewLine = true;

    @CommandLine.Option(names = "--trailing-commas", description = "Emit trailing commas in multi-line enums and array literals.")
    private boolean trailingCommas;

    @CommandLine.Option(
            names = "--stdin",
            description = "Read Java source from stdin; write formatted result to stdout.")
    private boolean stdin;

    /**
     * Framed stdio loop for IDE integrations: one request is {@code u32 BE source length} + UTF-8 source; one response
     * is {@code u32 BE status} (0 ok, 2 failure, 3 non-convergent) + {@code u32 BE payload length} + UTF-8 payload
     * (formatted source or error message). Big-endian per {@link java.io.DataInputStream#readInt()}. EOF on stdin
     * after a complete request exits successfully; malformed frames exit {@code 2}.
     */
    @CommandLine.Option(
            names = "--stdio-daemon",
            description =
                    "Run a framed stdin/stdout loop for repeated formatting (used by the VS Code extension). "
                            + "Mutually exclusive with --stdin and PATH arguments.")
    private boolean stdioDaemon;

    /** Upper bound on a single daemon request body to avoid accidental OOM from a corrupt length prefix. */
    private static final int STDIO_DAEMON_MAX_SOURCE_BYTES = 64 * 1024 * 1024;

    private static final int DAEMON_STATUS_OK = 0;

    @CommandLine.Option(
            names = {"-r", "--recursive"},
            description = "When arguments are directories, find .java files recursively.")
    private boolean recursive;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Log each file processed to stderr.")
    private boolean verbose;

    @CommandLine.Parameters(arity = "0..*", paramLabel = "PATH", description = ".java files or directories")
    private final List<Path> paths = new ArrayList<>();

    /**
     * Same {@link CommandLine} setup as {@link #main(String[])} — tests should use this so parse errors share the
     * production handlers (exit code {@code 2}).
     */
    static CommandLine applicationCommandLine() {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setExecutionExceptionHandler(
                (ex, commandLine, parseResult) -> {
                    errLine(requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
                    return 2;
                });
        cmd.setParameterExceptionHandler(
                (ex, args) -> {
                    errLine(requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
                    return 2;
                });
        return cmd;
    }

    static void errLine(String message) {
        synchronized (STDERR_LOCK) {
            System.err.println(message);
        }
    }

    public static void main(String[] args) {
        System.exit(applicationCommandLine().execute(args));
    }

    @Override
    public Integer call() {
        try {
            Formatter formatter =
                    new Formatter(
                            FormatterConfig.builder()
                                    .javaLanguageLevel(JavaLanguageLevel.of(javaVersion))
                                    .indentStyle(indentStyle)
                                    .indentSize(indentSize)
                                    .lineLength(lineLength)
                                    .wrapStyle(wrapStyle)
                                    .closingParenOnNewLine(closingParenOnNewLine)
                                    .trailingCommas(trailingCommas)
                                    .build());
            if (stdioDaemon && stdin) {
                errLine("error: --stdio-daemon cannot be combined with --stdin");
                return 2;
            }
            if (stdioDaemon) {
                if (!paths.isEmpty()) {
                    errLine("error: --stdio-daemon does not accept PATH arguments");
                    return 2;
                }
                return runStdioDaemon(formatter);
            }
            if (stdin) {
                return runStdin(formatter);
            }
            if (paths.isEmpty()) {
                errLine("error: no inputs (pass .java paths or directories, or use --stdin)");
                return 2;
            }
            List<Path> files = collectJavaFiles(paths, recursive);
            if (files.isEmpty()) {
                errLine("error: no .java files matched");
                return 2;
            }
            if (failFast && !check) {
                errLine("error: --fail-fast requires --check");
                return 2;
            }
            return runBatch(formatter, files);
        } catch (FormatterException | IOException e) {
            errLine(requireNonNullElse(e.getMessage(), e.getClass().getName()));
            return 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errLine("interrupted");
            return 2;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            String msg =
                    c != null
                            ? requireNonNullElse(c.getMessage(), c.getClass().getName())
                            : requireNonNullElse(e.getMessage(), e.getClass().getName());
            errLine(msg);
            return 2;
        } catch (IllegalArgumentException e) {
            errLine("error: " + e.getMessage());
            return 2;
        }
    }

    private static int runStdin(Formatter formatter) throws IOException {
        String input;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            input = r.lines().collect(Collectors.joining("\n"));
        }
        FormatResult result = formatter.formatResult(input);
        if (result instanceof FormatResult.Success success) {
            byte[] payload = bytesForStdoutFormattedPayload(success.formattedSource());
            System.out.write(payload);
            System.out.flush();
            return 0;
        }
        FormatResult.Failure failure = (FormatResult.Failure) result;
        errLine(failure.message());
        return failureExitCode(failure);
    }

    private static byte[] bytesForStdoutFormattedPayload(String formattedSource) {
        if (formattedSource.isEmpty()) {
            return new byte[0];
        }
        if (formattedSource.endsWith("\n")) {
            return formattedSource.getBytes(StandardCharsets.UTF_8);
        }
        return (formattedSource + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static int runStdioDaemon(Formatter formatter) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(System.in));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(System.out));
        try {
            while (true) {
                final int lenRaw;
                try {
                    lenRaw = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                long unsignedLen = Integer.toUnsignedLong(lenRaw);
                if (unsignedLen > STDIO_DAEMON_MAX_SOURCE_BYTES) {
                    writeDaemonFrame(out, 2, ("frame too large: " + unsignedLen).getBytes(StandardCharsets.UTF_8));
                    return 2;
                }
                int len = (int) unsignedLen;
                byte[] sourceBytes = new byte[len];
                in.readFully(sourceBytes);
                String input = new String(sourceBytes, StandardCharsets.UTF_8);
                FormatResult result = formatter.formatResult(input);
                if (result instanceof FormatResult.Success success) {
                    writeDaemonFrame(out, DAEMON_STATUS_OK, bytesForStdoutFormattedPayload(success.formattedSource()));
                } else {
                    FormatResult.Failure failure = (FormatResult.Failure) result;
                    writeDaemonFrame(out, failureExitCode(failure), failure.message().getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally {
            out.flush();
        }
        return 0;
    }

    private static void writeDaemonFrame(DataOutputStream out, int status, byte[] payload) throws IOException {
        out.writeInt(status);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private int runBatch(Formatter formatter, List<Path> files)
            throws IOException, InterruptedException, ExecutionException {
        if (check && failFast) {
            return runBatchCheckFailFast(formatter, files);
        }
        return runBatchParallel(formatter, files);
    }

    /**
     * Sequential {@code --check --fail-fast}: exit {@code 1} on the first file that would change.
     */
    private int runBatchCheckFailFast(Formatter formatter, List<Path> files) throws IOException {
        for (Path file : files) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            FormatResult result = formatter.formatResult(src, file);
            if (result instanceof FormatResult.Success success) {
                boolean unchanged = src.equals(success.formattedSource());
                if (verbose) {
                    errLine(file.toString());
                }
                if (!unchanged) {
                    errLine("check failed: " + file + " needs formatting");
                    return 1;
                }
            } else {
                FormatResult.Failure failure = (FormatResult.Failure) result;
                errLine(failure.message());
                return failureExitCode(failure);
            }
        }
        return 0;
    }

    @SuppressWarnings("PMD.CloseResource")
    private int runBatchParallel(Formatter formatter, List<Path> files)
            throws IOException, InterruptedException, ExecutionException {
        boolean anyChange = false;
        int workers = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<BatchResult>> futures = new ArrayList<>();
            for (Path file : files) {
                futures.add(
                        executor.submit(
                                () -> {
                                    String src = Files.readString(file, StandardCharsets.UTF_8);
                                    FormatResult result = formatter.formatResult(src, file);
                                    if (result instanceof FormatResult.Success success) {
                                        String out = success.formattedSource();
                                        boolean unchanged = src.equals(out);
                                        Optional<String> newContent =
                                                unchanged ? Optional.empty() : Optional.of(out);
                                        return new BatchResult(file, unchanged, newContent, Optional.empty());
                                    }
                                    return new BatchResult(
                                            file,
                                            true,
                                            Optional.empty(),
                                            Optional.of((FormatResult.Failure) result));
                                }));
            }
            for (Future<BatchResult> f : futures) {
                BatchResult r = f.get();
                if (r.failure().isPresent()) {
                    FormatResult.Failure failure = r.failure().get();
                    errLine(failure.message());
                    return failureExitCode(failure);
                }
                if (verbose) {
                    errLine(r.path().toString());
                }
                if (!r.unchanged()) {
                    anyChange = true;
                    if (!check) {
                        Files.writeString(r.path(), r.newContentIfChanged().orElseThrow(), StandardCharsets.UTF_8);
                    }
                }
            }
        } finally {
            executor.shutdown();
        }
        if (check && anyChange) {
            errLine("check failed: one or more files need formatting");
            return 1;
        }
        return 0;
    }

    /**
     * @param newContentIfChanged formatted source only when the output differs from the input; empty when unchanged to
     *     limit peak memory for large batches.
     * @param failure when non-empty, formatting failed for this file
     */
    private record BatchResult(
            Path path,
            boolean unchanged,
            Optional<String> newContentIfChanged,
            Optional<FormatResult.Failure> failure) {}

    private static int failureExitCode(FormatResult.Failure failure) {
        if (failure instanceof FormatResult.NonConvergent) {
            return EXIT_NON_CONVERGENT;
        }
        if (failure instanceof FormatResult.PathScopedFailure pathScoped
                && pathScoped.cause() instanceof FormatResult.NonConvergent) {
            return EXIT_NON_CONVERGENT;
        }
        return 2;
    }

    /**
     * Collects {@code .java} inputs from CLI paths.
     *
     * <p><b>Git vs walk:</b> When a directory sits inside a Git work tree and {@code recursive} is {@code true},
     * indexed and untracked {@code .java} paths come from {@code git ls-files} (two runs — tracked files and
     * {@code --others --exclude-standard} — merged so untracked-but-not-ignored sources are included). When there is
     * no Git root, or {@code recursive} is {@code false} on a Git directory, listing/walking does not use Git.
     */
    @SuppressWarnings("ConstantConditions")
    static List<Path> collectJavaFiles(List<Path> paths, boolean recursive) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path p : paths) {
            Path abs = p.toAbsolutePath().normalize();
            if (!Files.exists(abs)) {
                throw new IOException("No such path: " + p);
            }
            if (Files.isRegularFile(abs)) {
                if (abs.toString().endsWith(JAVA_FILE_SUFFIX)) {
                    out.add(abs);
                }
                continue;
            }
            if (Files.isDirectory(abs)) {
                Path gitRoot = findGitRoot(abs);
                if (gitRoot != null && recursive) {
                    Set<Path> merged = new LinkedHashSet<>();
                    merged.addAll(gitListedJavaFiles(gitRoot, abs, false));
                    merged.addAll(gitListedJavaFiles(gitRoot, abs, true));
                    out.addAll(merged);
                } else if (recursive) {
                    walkJavaFiles(abs, out);
                } else {
                    try (var stream = Files.list(abs)) {
                        stream.filter(Files::isRegularFile)
                                .filter(x -> x.toString().endsWith(JAVA_FILE_SUFFIX))
                                .forEach(out::add);
                    }
                }
            }
        }
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }

    /**
     * Walk parents of {@code start} to find a directory containing {@code .git} (either a repository
     * directory or a {@code gitdir:} pointer file as used by linked worktrees).
     */
    static @Nullable Path findGitRoot(Path start) {
        Path p = start.toAbsolutePath().normalize();
        while (p != null) {
            Path git = p.resolve(".git");
            if (Files.exists(git)) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    /**
     * Paths to {@code .java} files reported by {@code git ls-files} under {@code repoRoot}, limited to
     * those under {@code scope}. When {@code others} is true, uses {@code --others --exclude-standard}
     * so untracked (but not ignored) sources are included alongside the index.
     */
    private static List<Path> gitListedJavaFiles(Path repoRoot, Path scope, boolean others) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("ls-files");
        cmd.add("-z");
        if (others) {
            cmd.add("--others");
            cmd.add("--exclude-standard");
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot.toFile());
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        byte[] bytes = p.getInputStream().readAllBytes();
        int exit;
        try {
            exit = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git ls-files interrupted", e);
        }
        if (exit != 0) {
            throw new IOException("git ls-files failed in " + repoRoot);
        }
        Path scopeNorm = scope.toAbsolutePath().normalize();
        return javaFilesFromGitLsOutput(bytes, repoRoot, scopeNorm);
    }

    /**
     * Parses {@code git ls-files -z} output: paths separated by NUL bytes.
     */
    private static List<Path> javaFilesFromGitLsOutput(byte[] bytes, Path repoRoot, Path scopeNorm) {
        List<Path> list = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                String rel = new String(bytes, start, i - start, StandardCharsets.UTF_8);
                start = i + 1;
                if (rel.endsWith(JAVA_FILE_SUFFIX)) {
                    Path file = repoRoot.resolve(rel).normalize();
                    if (file.startsWith(scopeNorm)) {
                        list.add(file);
                    }
                }
            }
        }
        return list;
    }

    private static void walkJavaFiles(Path root, List<Path> out) throws IOException {
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (shouldSkipWalkSubtree(dir, root)) {
                            return SKIP_SUBTREE;
                        }
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && file.toString().endsWith(JAVA_FILE_SUFFIX)) {
                            out.add(file);
                        }
                        return CONTINUE;
                    }
                });
    }

    static boolean shouldSkipWalkSubtree(Path dir, Path root) {
        if (dir.equals(root)) {
            return false;
        }
        String name = dir.getFileName().toString();
        return switch (name) {
            case ".git",
                    "build",
                    "target",
                    "node_modules",
                    "out",
                    "bin",
                    ".gradle",
                    ".idea",
                    ".vscode",
                    "dist" -> true;
            default -> false;
        };
    }
}
