package io.princeofspace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EscapedNulJavadocReproducerTest {

    private static final String ESCAPED_NUL = "\\u0000";

    private static final FormatterConfig CONFIG = FormatterConfig.builder()
            .indentSize(2)
            .lineLength(100)
            .javaLanguageLevel(JavaLanguageLevel.of(25))
            .build();

    @ParameterizedTest
    @ValueSource(strings = {
            "/** Escaped NUL: %s */ class T {}",
            "/* Escaped NUL: %s */ class T {}",
            "// Escaped NUL: %s\nclass T {}",
            "class T { /** Escaped NUL: %s */ int value; }",
            "class T { /* Escaped NUL: %s */ int value; }",
            "class T { // Escaped NUL: %s\nint value; }",
            "class T { /* Escaped NUL: %s */ }",
            "class T { void m() { consume(1, /* Escaped NUL: %s */ 2); } }",
            "class T { void m() { consume(1 /* Escaped NUL: %s */, 2); } }",
            "class T { void m() { consume(/* Escaped NUL: %s */); } }",
            "class T { Object value = /* Escaped NUL: %s */ null; }",
            "class T { Object value = null /* Escaped NUL: %s */; }",
            "class T { Object m() { return /* Escaped NUL: %s */ null; } }",
            "class T { Object m() { return null /* Escaped NUL: %s */; } }",
            "class T { void m() { throw /* Escaped NUL: %s */ new Exception(); } }",
            "class T { void m() { throw new Exception() /* Escaped NUL: %s */; } }",
            "class T { Object[] values = {null /* Escaped NUL: %s */, null}; }",
            "class T { Object value /* Escaped NUL: %s */ = null; }",
            "/** Escaped NUL: %s */",
            "/* Escaped NUL: %s */",
            "// Escaped NUL: %s"
    })
    void preservesUnicodeInEveryCommentLocation(String sourceTemplate) throws CharacterCodingException {
        String content = "é \\u00E9 " + Character.toString(0x10401)
                + " \\uD801\\uDC01 \0 \\u0000 " + (char) 0xD801 + " \\uDC01";
        String expected = "é é " + Character.toString(0x10401) + " " + Character.toString(0x10401)
                + " \\u0000 \\u0000 \\uD801 \\uDC01";
        assertEscapedComment(sourceTemplate.formatted(content), "Escaped NUL: " + expected);
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("unicodeComments")
    void preservesRawAndEscapedUnicodeCommentContent(String source, String expected) throws CharacterCodingException {
        assertEscapedComment("/** Content: " + source + " */ class T {}", "Content: " + expected);
    }

    private static Stream<Arguments> unicodeComments() {
        String high = Character.toString(0xD801);
        String low = Character.toString(0xDC01);
        String highEscape = "\\uD801";
        String lowEscape = "\\uDC01";
        return Stream.of(
                Arguments.of("é漢", "é漢"),
                Arguments.of("\\u00E9\\u6F22", "é漢"),
                Arguments.of("e\u0301\uFEFF\uFDD0\uFFFE", "e\u0301\uFEFF\uFDD0\uFFFE"),
                Arguments.of("e\\u0301\\uFEFF\\uFDD0\\uFFFE", "e\u0301\uFEFF\uFDD0\uFFFE"),
                Arguments.of(high + low, high + low),
                Arguments.of("\\uD801\\uDC01", high + low),
                Arguments.of(high + lowEscape, high + low),
                Arguments.of(highEscape + low, high + low),
                Arguments.of(high, highEscape),
                Arguments.of(highEscape, highEscape),
                Arguments.of("\\u005c\\uD801", "\\u005C\\uD801"),
                Arguments.of(low, lowEscape),
                Arguments.of(lowEscape, lowEscape),
                Arguments.of(low + high, "\\uDC01\\uD801"),
                Arguments.of("\\uDC01\\uD801", "\\uDC01\\uD801"),
                Arguments.of(high + "x" + low, "\\uD801x\\uDC01"),
                Arguments.of("\\uD801x\\uDC01", "\\uD801x\\uDC01"),
                Arguments.of("\0", ESCAPED_NUL),
                Arguments.of(ESCAPED_NUL, ESCAPED_NUL));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\\uuuu0000 | \\u0000",
            "\\u0000\\u0000 | \\u0000\\u0000",
            "\\u005c\\u0000 | \\u005C\\u0000",
            "\\u005c\\u005c\\u0000 | \\\\\\u0000",
            "\\\\u0000 | \\\\u0000"
    })
    void preservesVisibleNulSpellingAndPrecedingBackslashes(String source, String expected) throws CharacterCodingException {
        assertEscapedComment("/** Escaped NUL: " + source + " */ class T {}", "Escaped NUL: " + expected);
    }

    private static void assertEscapedComment(String input, String expected) throws CharacterCodingException {
        Formatter singlePass = new Formatter(CONFIG, true);
        String firstPass = singlePass.format(input);
        assertThat(firstPass).doesNotContain("\0").containsOnlyOnce(expected);
        assertUtf8RoundTrip(firstPass);
        assertThat(singlePass.format(firstPass)).isEqualTo(firstPass);

        Formatter formatter = new Formatter(CONFIG);
        String output = formatter.format(input);
        assertThat(output).doesNotContain("\0").containsOnlyOnce(expected);
        assertUtf8RoundTrip(output);
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    private static void assertUtf8RoundTrip(String output) throws CharacterCodingException {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        assertThat(StandardCharsets.UTF_8.decode(encoder.encode(CharBuffer.wrap(output))).toString())
                .isEqualTo(output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD801", ESCAPED_NUL, "\\u005CuXXXX"})
    void normalizesMirroredCommentOnlyOnce(String source) throws CharacterCodingException {
        String input = "class T { int[] values = {1, 2, 3, // " + source
                + "\n4, 5, 6, // " + source + "\n7}; }";
        JavaParser parser = new JavaParser(new ParserConfiguration().setPreprocessUnicodeEscapes(true));
        String expected = parser.parse(input).getResult().orElseThrow().getAllComments().get(0).getContent();
        for (boolean singlePass : new boolean[] {false, true}) {
            Formatter formatter = new Formatter(CONFIG, singlePass);
            String output = formatter.format(input);
            assertUtf8RoundTrip(output);
            assertThat(parser.parse(output).getResult().orElseThrow().getAllComments())
                    .isNotEmpty()
                    .allSatisfy(comment -> assertThat(comment.getContent()).isEqualTo(expected));
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void measuresEscapedInlineCommentWidth(boolean singlePass) throws CharacterCodingException {
        Formatter formatter = new Formatter(FormatterConfig.builder().indentSize(2).lineLength(50).build(), singlePass);
        String escapes = "\\uD801".repeat(4);
        String input = "class T { void m() { consume(123 /* " + escapes + " */, 456, 789); } }";

        String output = formatter.format(input);

        assertThat(output).containsOnlyOnce("/* " + escapes + " */");
        assertUtf8RoundTrip(output);
        assertThat(output.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(50));
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void javadocCodePreservesEscapedNul() throws CharacterCodingException {
        String input = """
                class NulComment {
                  /**
                   * Escaped NUL: {@code \\u0000}
                   */
                  int value;
                }
                """;
        assertEscapedComment(input, "Escaped NUL: {@code \\u0000}");
    }
}
