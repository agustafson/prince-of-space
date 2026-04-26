package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
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
 * against golden files under {@code examples/outputs/}. Prefer fixing the formatter to match; after
 * agreed behavior changes, regenerate with {@code RegenerateShowroomGoldens} (see {@code REGENERATE_SHOWROOM}).
 *
 * <p>Showroom goldens also run under the default {@code ./gradlew :core:test} task. To run only this
 * suite: {@code ./gradlew :core:showroomGoldenTest}.
 */
@Tag("showroom-golden")
class FormatterShowcaseGoldenTest {

    static Stream<Arguments> cases() {
        Path root = repoRoot();
        Path inputs = root.resolve("examples/inputs");
        Path outputs = root.resolve("examples/outputs");
        JavaLanguageLevel[] levels = {
            JavaLanguageLevel.of(8), JavaLanguageLevel.of(17),
            JavaLanguageLevel.of(21), JavaLanguageLevel.of(25)
        };
        String[] goldens = {
            "balanced-closingparen-false.java",
            "balanced-closingparen-true.java",
            "narrow-closingparen-false.java",
            "narrow-closingparen-true.java",
            "wide-closingparen-false.java",
            "wide-closingparen-true.java",
        };
        Stream.Builder<Arguments> b = Stream.builder();
        for (JavaLanguageLevel level : levels) {
            String dir = showcaseDirFor(level);
            for (String name : goldens) {
                b.add(Arguments.of(level, name, inputs.resolve(dir).resolve("FormatterShowcase.java"), outputs.resolve(dir).resolve(name)));
            }
        }
        return b.build();
    }

    static Path repoRoot() {
        return RepoPaths.repoRoot();
    }

    /**
     * Maps matrix filename segments to {@link FormatterConfig}.
     */
    static FormatterConfig formatterConfigFor(JavaLanguageLevel level, String goldenFileName) {
        String base = goldenFileName.replace(".java", "");
        String[] p = base.split("-", 3);
        if (p.length != 3) {
            throw new IllegalArgumentException("Unexpected golden name: " + goldenFileName);
        }
        WrapStyle wrap = WrapStyle.valueOf(p[0].toUpperCase(Locale.ROOT));
        if (!"closingparen".equals(p[1])) {
            throw new IllegalArgumentException("Expected closingparen segment: " + goldenFileName);
        }
        boolean closing = Boolean.parseBoolean(p[2]);
        return FormatterConfig.builder()
                .wrapStyle(wrap)
                .closingParenOnNewLine(closing)
                .javaLanguageLevel(level)
                .build();
    }

    static String showcaseDirFor(JavaLanguageLevel level) {
        return switch (level.level()) {
            case 8 -> "java8";
            case 17 -> "java17";
            case 21 -> "java21";
            case 25 -> "java25";
            default -> throw new IllegalArgumentException("Unsupported showcase language level: " + level);
        };
    }

    private static String nl(String s) {
        return s.replace("\r\n", "\n");
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("cases")
    void matchesGolden(JavaLanguageLevel level, String goldenName, Path input, Path goldenPath) throws IOException {
        String source = Files.readString(input, StandardCharsets.UTF_8);
        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        Formatter f = new Formatter(formatterConfigFor(level, goldenName));
        assertThat(nl(f.format(source))).isEqualTo(nl(expected));
    }
}
