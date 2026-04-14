package io.princeofspace;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves repository paths for tests regardless of whether Gradle's working directory is the
 * repo root, {@code core/}, or {@code modules/core/}.
 */
public final class RepoPaths {

    private RepoPaths() {}

    /** Directory that contains {@code examples/inputs} (repository root). */
    public static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path p = start;
        for (int i = 0; i < 12 && p != null; i++) {
            if (Files.isDirectory(p.resolve("examples/inputs"))) {
                return p;
            }
            p = p.getParent();
        }
        Path name = start.getFileName();
        if (name != null && "core".equals(name.toString())) {
            Path parent = start.getParent();
            if (parent != null && Files.isDirectory(parent.resolve("examples/inputs"))) {
                return parent;
            }
            Path grand = parent == null ? null : parent.getParent();
            if (grand != null && Files.isDirectory(grand.resolve("examples/inputs"))) {
                return grand;
            }
        }
        throw new IllegalStateException(
                "Cannot resolve repo root from " + start + " (expected an ancestor containing examples/inputs)");
    }

    /** {@link #repoRoot()}{@code /examples}. */
    public static Path examplesRoot() {
        return repoRoot().resolve("examples");
    }
}
