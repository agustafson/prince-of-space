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
        LanguageLevel[] levels = {
            LanguageLevel.JAVA_8, LanguageLevel.JAVA_17, LanguageLevel.JAVA_21, LanguageLevel.JAVA_25
        };
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
        for (LanguageLevel level : levels) {
            String dir = showcaseDirFor(level);
            for (String name : goldens) {
                b.add(Arguments.of(level, name, inputs.resolve(dir).resolve("FormatterShowcase.java"), outputs.resolve(dir).resolve(name)));
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
     * Maps matrix filename segments to {@link FormatterConfig}.
     */
    static FormatterConfig formatterConfigFor(LanguageLevel level, String goldenFileName) {
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
        return FormatterConfig.builder()
                .wrapStyle(wrap)
                .continuationIndentSize(cont)
                .closingParenOnNewLine(closing)
                .javaLanguageLevel(level)
                .build();
    }

    private static String showcaseDirFor(LanguageLevel level) {
        return switch (level) {
            case JAVA_8 -> "java8";
            case JAVA_17 -> "java17";
            case JAVA_21 -> "java21";
            case JAVA_25 -> "java25";
            default -> throw new IllegalArgumentException("Unsupported showcase language level: " + level);
        };
    }

    private static String nl(String s) {
        return s.replace("\r\n", "\n");
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("cases")
    void matchesGolden(LanguageLevel level, String goldenName, Path input, Path goldenPath) throws IOException {
        String source = Files.readString(input, StandardCharsets.UTF_8);
        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        Formatter f = new Formatter(formatterConfigFor(level, goldenName));
        assertThat(nl(f.format(source))).isEqualTo(nl(expected));
    }
}
