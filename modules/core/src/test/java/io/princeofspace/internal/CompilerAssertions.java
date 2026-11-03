package io.princeofspace.internal;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Compiles saved source independently of JavaParser and invokes {@code EscapeProbe.value()}. */
final class CompilerAssertions {

    private CompilerAssertions() {}

    static String compileAndInvoke(Path directory, String source) throws IOException, ReflectiveOperationException {
        Path sourceFile = saveSource(directory, source);

        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Tests require a JDK compiler").isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean compiled = compiler.getTask(null, files, diagnostics,
                    List.of("--release", "17", "-proc:none", "-encoding", "UTF-8", "-d", directory.toString()),
                    null, files.getJavaFileObjects(sourceFile)).call();
            assertThat(compiled).as("Compiler diagnostics: %s", diagnostics.getDiagnostics()).isTrue();
        }

        return invoke(directory);
    }

    static String compileAndInvoke(Path directory, String source, Path javac)
            throws IOException, ReflectiveOperationException, InterruptedException {
        Path sourceFile = saveSource(directory, source);
        run(directory, "javac", javac.toString(), "-proc:none", "-encoding", "UTF-8",
                "-d", directory.toString(), sourceFile.toString());
        // Loading older bytecode in the test worker adds JaCoCo fields to the field-count fixture.
        return run(directory, "java", javac.resolveSibling("java").toString(), "-ea", "-cp",
                directory.toString(), "EscapeProbe");
    }

    private static String run(Path directory, String tool, String... command) throws IOException, InterruptedException {
        Path diagnostics = directory.resolve(tool + ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).redirectOutput(diagnostics.toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("%s timed out", tool).isTrue();
            String output = Files.readString(diagnostics);
            assertThat(process.exitValue()).as("%s diagnostics: %s", tool, output).isZero();
            return output;
        } finally {
            process.destroyForcibly();
        }
    }

    private static Path saveSource(Path directory, String source) throws IOException {
        Files.createDirectories(directory);
        Path sourceFile = directory.resolve("EscapeProbe.java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(Files.readString(sourceFile, StandardCharsets.UTF_8)).isEqualTo(source);
        return sourceFile;
    }

    private static String invoke(Path directory) throws IOException, ReflectiveOperationException {
        try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            loader.setDefaultAssertionStatus(true);
            return (String) loader.loadClass("EscapeProbe").getMethod("value").invoke(null);
        }
    }
}
