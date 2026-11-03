package io.princeofspace.internal;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Compiles saved source independently of JavaParser and invokes {@code EscapeProbe.value()}. */
final class CompilerAssertions {

    private CompilerAssertions() {}

    static String compileAndInvoke(Path directory, String source) throws IOException, ReflectiveOperationException {
        Files.createDirectories(directory);
        Path sourceFile = directory.resolve("EscapeProbe.java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(Files.readString(sourceFile, StandardCharsets.UTF_8)).isEqualTo(source);

        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Tests require a JDK compiler").isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean compiled = compiler.getTask(null, files, diagnostics,
                    List.of("--release", "17", "-proc:none", "-encoding", "UTF-8", "-d", directory.toString()),
                    null, files.getJavaFileObjects(sourceFile)).call();
            assertThat(compiled).as("Compiler diagnostics: %s", diagnostics.getDiagnostics()).isTrue();
        }

        try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            loader.setDefaultAssertionStatus(true);
            return (String) loader.loadClass("EscapeProbe").getMethod("value").invoke(null);
        }
    }
}
