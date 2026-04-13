package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional integration test: set {@code PRINCE_REAL_WORLD_ROOT} to a checkout of a Java project
 * (or any tree of sources). Every {@code .java} file is formatted with default config; failures
 * indicate parse or idempotency bugs on real code.
 */
class OptionalRealWorldCheckoutFormatTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "PRINCE_REAL_WORLD_ROOT", matches = ".+")
    void formatAllJavaUnderEnvRoot() throws IOException {
        Path root = Path.of(System.getenv("PRINCE_REAL_WORLD_ROOT")).toAbsolutePath().normalize();
        assertThat(Files.isDirectory(root)).as("PRINCE_REAL_WORLD_ROOT must be a directory: %s", root).isTrue();

        Formatter f = new Formatter(FormatterConfig.defaults());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .filter(p -> !p.toString().contains("/.git/"))
                    .forEach(files::add);
        }
        assertThat(files).as("no .java files under %s", root).isNotEmpty();

        long t0 = System.nanoTime();
        for (Path p : files) {
            String src = Files.readString(p);
            String once = f.format(src);
            assertThat(f.format(once)).as("%s", p).isEqualTo(once);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(ms).isGreaterThanOrEqualTo(0L);
    }
}
