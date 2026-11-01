package io.princeofspace;

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
                Arguments.of("\\u005c\\uD801", "\\\\uD801"),
                Arguments.of(low, lowEscape),
                Arguments.of(lowEscape, lowEscape),
                Arguments.of(low + high, "\\uDC01\\uD801"),
                Arguments.of("\\uDC01\\uD801", "\\uDC01\\uD801"),
                Arguments.of(high + "x" + low, "\\uD801x\\uDC01"),
                Arguments.of("\\uD801x\\uDC01", "\\uD801x\\uDC01"),
                Arguments.of("\0", "\\u0000"),
                Arguments.of("\\u0000", "\\u0000"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\\uuuu0000 | \\u0000",
            "\\u0000\\u0000 | \\u0000\\u0000",
            "\\u005c\\u0000 | \\\\u0000",
            "\\u005c\\u005c\\u0000 | \\\\\\u0000",
            "\\\\u0000 | \\\\u0000"
    })
    void preservesVisibleNulSpellingAndPrecedingBackslashes(String source, String expected) throws CharacterCodingException {
        assertEscapedComment("/** Escaped NUL: " + source + " */ class T {}", "Escaped NUL: " + expected);
    }

    private static void assertEscapedComment(String input, String expected) throws CharacterCodingException {
        Formatter singlePass = new Formatter(CONFIG, true);
        String firstPass = singlePass.format(input);
        assertThat(firstPass).doesNotContain("\0").contains(expected);
        assertUtf8RoundTrip(firstPass);
        assertThat(singlePass.format(firstPass)).isEqualTo(firstPass);

        Formatter formatter = new Formatter(CONFIG);
        String output = formatter.format(input);
        assertThat(output).doesNotContain("\0").contains(expected);
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
