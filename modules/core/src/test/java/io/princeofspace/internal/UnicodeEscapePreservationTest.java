package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static io.princeofspace.internal.CompilerAssertions.compileAndInvoke;
import static org.assertj.core.api.Assertions.assertThat;

class UnicodeEscapePreservationTest {

    private static final FormatterConfig CONFIG = FormatterConfig.builder().lineLength(60).build();
    private static final String FORWARD = "\\u005C\\\\u006e";
    private static final String REVERSE = "\\u005C\\\\\\u006e";

    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void preservesCompiledValue(String name, String members, String expected) throws Exception {
        String source = "public class EscapeProbe { " + members + " }";
        assertThat(compileAndInvoke(directory.resolve("input"), source)).isEqualTo(expected);

        Formatter singlePass = new Formatter(CONFIG, true);
        String first = singlePass.format(source);
        assertThat(compileAndInvoke(directory.resolve("pass1"), first)).isEqualTo(expected);

        Formatter formatter = new Formatter(CONFIG);
        String formatted = formatter.format(source);
        assertThat(compileAndInvoke(directory.resolve("strict"), formatted)).isEqualTo(expected);
        assertThat(formatter.format(formatted)).isEqualTo(formatted);
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals") // Keep the Java source fixtures readable without fragment constants.
    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("line comment escape stays inactive", method("String s = \"after\";\n// \\u005Cu000A s=\"before\";\nreturn s;"), "after"),
                Arguments.of("block comment escape stays inactive", method("String s = \"after\";\n/* \\u005Cu002A/ s=\"before\"; // */\nreturn s;"), "after"),
                Arguments.of("initializer", "static String text = \"" + FORWARD + "\"; " + method("return text;"), "\\n"),
                Arguments.of("return literal", method("return \"" + FORWARD + "\";"), "\\n"),
                Arguments.of("reverse literal", method("return \"" + REVERSE + "\";"), "\\\\u006e"),
                Arguments.of("assert message", method("try { assert false : \"" + FORWARD
                        + "\"; } catch (AssertionError failure) { return failure.getMessage(); } return \"disabled\";"), "\\n"),
                Arguments.of("concatenation", method("return \"" + FORWARD + "\" + \"x\";"), "\\nx"),
                Arguments.of("wrapped literal", method("return \"" + (FORWARD + " word ").repeat(20) + "\";"), "\\n word ".repeat(20)),
                Arguments.of("keywords and identifiers", "static String t\\u0065xt = \"value\"; public static String value() { ret\\uuuu0075rn text; }", "value"),
                Arguments.of("UTF8 controls and surrogates", method("return \"\\u0000\\u0085\\uD800x\\uDC00😀\";"), "\0\u0085\uD800x\uDC00😀"));
    }

    private static String method(String body) {
        return "public static String value() { " + body + " }";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedSources")
    void reportsMalformedUnicodeBeforeParsing(String source) {
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(CONFIG, singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains("Invalid Unicode escape at source offset"));
        }
    }

    static Stream<String> malformedSources() {
        return Stream.of("class T {} // \\u", "class T {} /* \\uZZZZ */", "class T {} /** \\uu12 */",
                "class T { String value = \"\\u００４１\"; }", "class T {} \\u000g", "class T {} // \\u005C\\\\uZZZZ");
    }

    @Test
    void retainsIneligibleUnicodeTextAndCommentAdjacency() throws Exception {
        String source = "public class EscapeProbe { public static String value() { "
                + "return /* \\u005Cu000A \\uD800 */ \"ok\" /* \\u0000 \\\\uZZZZ */; } }";
        Formatter formatter = new Formatter(CONFIG);
        String output = formatter.format(source);
        assertThat(output).contains("/* \\u005Cu000A \\uD800 */ \"ok\"", "\"ok\" /* \\u0000 \\u005C\\u005CuZZZZ */;");
        assertThat(compileAndInvoke(directory, output)).isEqualTo("ok");
        assertThat(formatter.format(output)).isEqualTo(output);
    }
}
