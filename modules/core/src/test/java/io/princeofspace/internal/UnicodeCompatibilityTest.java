package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static io.princeofspace.internal.CompilerAssertions.compileAndInvoke;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Keep the raw source fixtures readable.
class UnicodeCompatibilityTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {8, 11, 17, 21, 25})
    void rejectsCompilerDependentCommentBoundaries(int release) {
        String ambiguous = "\\u005C" + "\\\\" + "u000A";
        String source = "class T { void run() { int value = 0;\n// " + ambiguous + " value = 1;\n} }";
        int offset = source.indexOf(ambiguous) + 7;
        FormatterConfig config = FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(release)).build();
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(config, singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains(
                            "Compiler-dependent Unicode escape", "source offset " + offset, "JDK 8/11", "JDK 17+"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousSources")
    void rejectsAmbiguitiesOutsideLiteralContent(String name, String source) {
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(FormatterConfig.defaults(), singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains("Compiler-dependent Unicode escape"));
        }
    }

    static Stream<Arguments> ambiguousSources() {
        String forward = "\\u005C" + "\\\\";
        String reverse = "\\u005C" + "\\\\\\";
        return Stream.of(
                Arguments.of("LF ends comment", "class T { void run() { int s = 0;\n// " + forward + "u000A s=1;\n} }"),
                Arguments.of("LF stays in comment", "class T { void run() { int s = 0;\n// " + reverse + "u000A s=1;\n} }"),
                Arguments.of("CR ends comment", "class T {} // " + forward + "u000D int s;"),
                Arguments.of("CR stays in comment", "class T {} // " + reverse + "u000D int s;"),
                Arguments.of("star closes block comment", "class T {} /* " + forward + "u002A/ // */"),
                Arguments.of("star stays in block comment", "class T {} /* " + reverse + "u002A/ // */"),
                Arguments.of("slash closes block comment", "class T {} /* " + forward + "u002A\\u002F // */"),
                Arguments.of("repeated u", "class T {} // " + forward + "uuuu000A"),
                Arguments.of("malformed only under legacy interpretation", "class T {} // " + reverse + "uZZZZ"),
                Arguments.of("harmless comment text", "class T {} // " + forward + "u0078"),
                Arguments.of("comment-only source", "// " + reverse + "u000A"),
                Arguments.of("Javadoc", "/** " + reverse + "u000A */ class T {}"),
                Arguments.of("string closing delimiter", "class T { String value = \"" + forward + "u0022; }"),
                Arguments.of("char closing delimiter", "class T { char value = '" + forward + "u0027; }"),
                Arguments.of("literal before ambiguity", "class T { String value = \"" + forward + "u006e\"; } // "
                        + forward + "u000A"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("literalPrograms")
    void permitsMixedEscapesInsideLiterals(String name, String source) throws Exception {
        String expected = compileAndInvoke(directory.resolve("before"), source);
        for (boolean singlePass : new boolean[] {true, false}) {
            Formatter formatter = new Formatter(FormatterConfig.defaults(), singlePass);
            String output = formatter.format(source);
            assertThat(compileAndInvoke(directory.resolve("after-" + singlePass), output)).isEqualTo(expected);
            if (!singlePass) {
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    static Stream<Arguments> literalPrograms() {
        String forward = "\\u005C" + "\\\\";
        String reverse = "\\u005C" + "\\\\\\";
        String prefix = "public class EscapeProbe { public static String value() { ";
        String suffix = " } }";
        return Stream.of(
                Arguments.of("forward string", prefix + "return \"" + forward + "u006e\";" + suffix),
                Arguments.of("reverse string", prefix + "return \"" + reverse + "u006e\";" + suffix),
                Arguments.of("ineligible malformed string", prefix + "return \"" + reverse + "uZZZZ\";" + suffix),
                Arguments.of("multiple literals", prefix + "return \"" + forward + "u006e\" + \"" + reverse + "u006e\";" + suffix),
                Arguments.of("text block", prefix + "return \"\"\"\n" + forward + "u006e\n" + reverse + "u006e\n\"\"\";" + suffix),
                Arguments.of("leading trivia and CRLF", "\t// header\r\n" + prefix + "return \"" + forward + "u006e\";" + suffix),
                Arguments.of("generics and shifts", prefix + "java.util.List<java.util.List<String>> list = null; "
                        + "int n = 8 >>> 1; return \"" + forward + "u006e\" + (list == null ? n : 0);" + suffix));
    }
}
