package io.princeofspace;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formats every {@code .java} file under {@code examples/outputs} (golden files produced by
 * {@code RegenerateShowroomGoldens}). These must be fixed points: {@code format(format(x)) == format(x)}.
 * <p>
 * {@code examples/inputs} are hand-maintained and are only required to parse and format once for
 * showroom tests; they are not included here because the full inputs are not always idempotent
 * under double-formatting with the current printer.
 */
class ExamplesCorpusFormatTest {

    static Path examplesDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path here = cwd.resolve("examples");
        Path parent = cwd.getParent();
        Path up = parent == null ? null : parent.resolve("examples");
        if (Files.isDirectory(here)) {
            return here;
        }
        if (up != null && Files.isDirectory(up)) {
            return up;
        }
        throw new IllegalStateException(
                "examples/ not found (tried " + here + " and " + up + "); run tests from repo root or core/");
    }

    static LanguageLevel languageLevelFor(Path file) {
        for (int i = 0; i < file.getNameCount(); i++) {
            String seg = file.getName(i).toString();
            if ("java8".equals(seg)) {
                return LanguageLevel.JAVA_8;
            }
            if ("java17".equals(seg)) {
                return LanguageLevel.JAVA_17;
            }
            if ("java21".equals(seg)) {
                return LanguageLevel.JAVA_21;
            }
        }
        return LanguageLevel.JAVA_17;
    }

    @Test
    void formatGoldenOutputsTree_idempotent() throws IOException {
        Path root = examplesDirectory().resolve("outputs");
        assertThat(Files.isDirectory(root)).as("missing %s", root).isTrue();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        assertThat(files).as("under %s", root).isNotEmpty();

        for (Path p : files) {
            LanguageLevel level = languageLevelFor(p);
            Formatter f = new Formatter(FormatterConfig.builder().javaLanguageLevel(level).build());
            String src = Files.readString(p);
            String once = f.format(src);
            assertThat(f.format(once)).as("%s", p).isEqualTo(once);
        }
    }

    @Test
    void formatShowcaseInputs_singlePassSucceeds() throws IOException {
        Path root = examplesDirectory().resolve("inputs");
        assertThat(Files.isDirectory(root)).as("missing %s", root).isTrue();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        assertThat(files).isNotEmpty();
        for (Path p : files) {
            LanguageLevel level = languageLevelFor(p);
            Formatter f = new Formatter(FormatterConfig.builder().javaLanguageLevel(level).build());
            f.format(Files.readString(p));
        }
    }
}
