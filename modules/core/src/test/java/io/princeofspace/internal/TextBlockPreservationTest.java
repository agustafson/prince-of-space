package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static io.princeofspace.internal.CompilerAssertions.compileAndInvoke;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class TextBlockPreservationTest {

    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void preservesCompiledValue(String name, String source) throws IOException, ReflectiveOperationException {
        String expected = compileAndInvoke(directory.resolve("original"), source);
        assertPreserved(source, expected, FormatterConfig.defaults(), directory.resolve(name));
    }

    static Stream<Arguments> sources() {
        String blankLines = source("a\n\n\nb\n");
        return Stream.of(
                Arguments.of("blank-lines", blankLines),
                Arguments.of("braces", source("{\n\n}\n")),
                Arguments.of("blank-only", source("\n \n\t\n")),
                Arguments.of("leading-trailing-blanks", source("\n\na\n\n\n")),
                Arguments.of("whitespace", source("    first \t\n \t\n\n    second\\s \n    ")),
                Arguments.of("continuation", source("    first\\\n\n\n    second\n    ")),
                Arguments.of("escaped-delimiter", source("    \\\"\"\"\n\n\n    tail\n    ")),
                Arguments.of("inline-closing-delimiter", source("a\n\n\nb")),
                Arguments.of("crlf", blankLines.replace("\n", "\r\n")),
                Arguments.of("cr", blankLines.replace("\n", "\r")),
                Arguments.of("unicode-delimiters", blankLines.replace("\"\"\"", "\\u0022".repeat(3))),
                Arguments.of("unicode-newlines", source("a\\u000a\\u000a\\u000ab\n")),
                Arguments.of("unicode-braces", source("\\u007b\n\n\\u007d\n")),
                Arguments.of("escaped-controls", source("a\\u0000\n\n\n\\uD800b\n")),
                Arguments.of("whitespace-controls", source("a\n\f\n\t\n\nb\n")));
    }

    @Test
    void preservesMultipleBlocksInArraysLambdasAndChainsAcrossConfigurations()
            throws IOException, ReflectiveOperationException {
        String literal = "\"\"\"\n    {\n\n    }\n    a\n\n\n    b\n    \"\"\"";
        String source = "public class EscapeProbe {\n"
                + "public static String value() {\n"
                + "java.util.List<java.util.List<String>> unused = null; int shift = 32 >>> 2;\n"
                + "String[] values = { /* \"\"\" before */ " + literal + ", " + literal + " /* after */ };\n"
                + "java.util.function.Supplier<String> supplier = () -> { return " + literal + "; };\n"
                + "return values[0] + values[1] + supplier.get() + " + literal + ".stripIndent();\n"
                + "}\n} // trailing EOF";
        String expected = compileAndInvoke(directory.resolve("original"), source);
        for (WrapStyle wrapStyle : WrapStyle.values()) {
            for (IndentStyle indentStyle : IndentStyle.values()) {
                for (boolean closingParen : new boolean[] {false, true}) {
                    FormatterConfig config = FormatterConfig.builder()
                            .wrapStyle(wrapStyle)
                            .indentStyle(indentStyle)
                            .indentSize(indentStyle == IndentStyle.TABS ? 1 : 4)
                            .closingParenOnNewLine(closingParen)
                            .lineLength(60)
                            .build();
                    assertPreserved(source, expected, config,
                            directory.resolve(wrapStyle + "-" + indentStyle + "-" + closingParen));
                }
            }
        }
    }

    private static String source(String content) {
        return "public class EscapeProbe {\npublic static String value() { return \"\"\"\n"
                + content + "\"\"\"; }\n}\n";
    }

    private static void assertPreserved(String source, String expected, FormatterConfig config, Path directory)
            throws IOException, ReflectiveOperationException {
        String first = new Formatter(config, true).format(source);
        assertThat(compileAndInvoke(directory.resolve("first"), first)).isEqualTo(expected);
        Formatter formatter = new Formatter(config);
        String formatted = formatter.format(source);
        assertThat(compileAndInvoke(directory.resolve("strict"), formatted)).isEqualTo(expected);
        assertThat(formatter.format(formatted)).isEqualTo(formatted);
    }
}
