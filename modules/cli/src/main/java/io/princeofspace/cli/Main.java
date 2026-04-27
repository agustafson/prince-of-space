package io.princeofspace.cli;

import io.princeofspace.Formatter;
import io.princeofspace.FormatterException;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.io.BufferedReader;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

/**
 * Command-line entry for formatting Java sources. See {@code --help} for options.
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

    @CommandLine.Option(
            names = "--check",
            description = "Check only; exit 1 if any file would change (does not write files).")
    private boolean check;

    @CommandLine.Option(
            names = "--java-version",
            description =
                    "Java language level for parsing (1–7 legacy, 8+ maps to JavaParser LanguageLevel.JAVA_N; "
                            + "newer releases work when the bundled JavaParser defines the enum constant)",
            defaultValue = "17")
    private int javaVersion;

    @CommandLine.Option(
            names = "--stdin",
            description = "Read Java source from stdin; write formatted result to stdout.")
    private boolean stdin;

    @CommandLine.Option(
            names = {"-r", "--recursive"},
            description = "When arguments are directories, find .java files recursively.")
    private boolean recursive;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Log each file processed to stderr.")
    private boolean verbose;

    @CommandLine.Parameters(arity = "0..*", paramLabel = "PATH", description = ".java files or directories")
    private final List<Path> paths = new ArrayList<>();

    public static void main(String[] args) {
        int code = new CommandLine(new Main()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        try {
            Formatter formatter =
                    new Formatter(
                            FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(javaVersion)).build());
            if (stdin) {
                return runStdin(formatter);
            }
            if (paths.isEmpty()) {
                System.err.println("error: no inputs (pass .java paths or directories, or use --stdin)");
                return 2;
            }
            List<Path> files = collectJavaFiles(paths, recursive);
            if (files.isEmpty()) {
                System.err.println("error: no .java files matched");
                return 2;
            }
            return runBatch(formatter, files);
        } catch (FormatterException | IOException e) {
            System.err.println(e.getMessage());
            return 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("interrupted");
            return 2;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            System.err.println(c != null ? c.getMessage() : e.getMessage());
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }
    }

    private static int runStdin(Formatter formatter) throws IOException {
        String input;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            input = r.lines().collect(Collectors.joining("\n"));
        }
        String out = formatter.format(input);
        System.out.print(out);
        if (!out.isEmpty() && !out.endsWith("\n")) {
            System.out.println();
        }
        return 0;
    }

    @SuppressWarnings("PMD.CloseResource")
    private int runBatch(Formatter formatter, List<Path> files)
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
                                    String out = formatter.format(src, file);
                                    return new BatchResult(file, src.equals(out), out);
                                }));
            }
            for (Future<BatchResult> f : futures) {
                BatchResult r = f.get();
                if (verbose) {
                    System.err.println(r.path());
                }
                if (!r.unchanged()) {
                    anyChange = true;
                    if (!check) {
                        Files.writeString(r.path(), r.formatted(), StandardCharsets.UTF_8);
                    }
                }
            }
        } finally {
            executor.shutdown();
        }
        if (check && anyChange) {
            System.err.println("check failed: one or more files need formatting");
            return 1;
        }
        return 0;
    }

    private record BatchResult(Path path, boolean unchanged, String formatted) {}

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
                        String name = dir.getFileName().toString();
                        if (dir.equals(root)) {
                            return CONTINUE;
                        }
                        if (name.equals(".git") || name.equals("build") || name.equals("target")) {
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
}
