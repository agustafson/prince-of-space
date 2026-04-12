package io.princeofspace;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares {@link Formatter#format(String)} on each {@code examples/inputs/.../FormatterShowcase.java}
 * against the <strong>authoritative</strong> golden files under {@code examples/outputs/}. Those files
 * are the specification; adjust the formatter until they match — do not overwrite goldens to silence
 * tests.
 *
 * <p>Run with {@code ./gradlew :core:showroomGoldenTest} (excluded from the default {@code test} task
 * until showroom parity is complete).
 */
@Tag("showroom-golden")
class FormatterShowcaseGoldenTest {

    static Stream<Arguments> cases() {
        Path root = repoRoot();
        Path inputs = root.resolve("examples/inputs");
        Path outputs = root.resolve("examples/outputs");
        String[] levels = {"java8", "java17", "java21"};
        String[] goldens = {
            "balanced-cont4-closingparen-false.java",
            "balanced-cont4-closingparen-true.java",
            "balanced-cont8-closingparen-false.java",
            "balanced-cont8-closingparen-true.java",
            "narrow-cont4-closingparen-false.java",
            "narrow-cont4-closingparen-true.java",
            "narrow-cont8-closingparen-false.java",
            "narrow-cont8-closingparen-true.java",
            "wide-cont4-closingparen-false.java",
            "wide-cont4-closingparen-true.java",
            "wide-cont8-closingparen-false.java",
            "wide-cont8-closingparen-true.java",
        };
        Stream.Builder<Arguments> b = Stream.builder();
        for (String level : levels) {
            for (String name : goldens) {
                b.add(Arguments.of(level, name, inputs.resolve(level).resolve("FormatterShowcase.java"), outputs.resolve(level).resolve(name)));
            }
        }
        return b.build();
    }

    static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cwdName = cwd.getFileName();
        if (cwdName != null && "core".equals(cwdName.toString())) {
            Path parent = cwd.getParent();
            if (parent == null) {
                throw new IllegalStateException("Cannot resolve repo root from " + cwd);
            }
            return parent;
        }
        return cwd;
    }

    /**
     * Maps matrix filename segments to {@link FormatterConfig}. Parser language level must accept the
     * showcase source; {@code java17} inputs use syntax JavaParser only parses from {@code JAVA_21}.
     */
    static FormatterConfig formatterConfigFor(String level, String goldenFileName) {
        String base = goldenFileName.replace(".java", "");
        String[] p = base.split("-", 4);
        if (p.length != 4) {
            throw new IllegalArgumentException("Unexpected golden name: " + goldenFileName);
        }
        WrapStyle wrap = WrapStyle.valueOf(p[0].toUpperCase(Locale.ROOT));
        if (!p[1].startsWith("cont")) {
            throw new IllegalArgumentException("Expected cont<N> segment: " + goldenFileName);
        }
        int cont = Integer.parseInt(p[1].substring(4));
        if (!"closingparen".equals(p[2])) {
            throw new IllegalArgumentException("Expected closingparen segment: " + goldenFileName);
        }
        boolean closing = Boolean.parseBoolean(p[3]);
        LanguageLevel lang =
                switch (level) {
                    case "java8" -> LanguageLevel.JAVA_8;
                    case "java17" -> LanguageLevel.JAVA_21;
                    case "java21" -> LanguageLevel.JAVA_21;
                    default -> throw new IllegalArgumentException(level);
                };
        return FormatterConfig.builder()
                .wrapStyle(wrap)
                .continuationIndentSize(cont)
                .closingParenOnNewLine(closing)
                .javaLanguageLevel(lang)
                .build();
    }

    private static String nl(String s) {
        return s.replace("\r\n", "\n");
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("cases")
    void matchesGolden(String level, String goldenName, Path input, Path goldenPath) throws IOException {
        String source = Files.readString(input, StandardCharsets.UTF_8);
        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        Formatter f = new Formatter(formatterConfigFor(level, goldenName));
        assertThat(nl(f.format(source))).isEqualTo(nl(expected));
    }
}
