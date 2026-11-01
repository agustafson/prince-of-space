package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void preservesNulInEveryCommentLocation(String sourceTemplate) {
        assertEscapedComment(sourceTemplate.formatted("\\u0000"), "Escaped NUL: \\u0000");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\\uuuu0000 | \\u0000",
            "\\u0000\\u0000 | \\u0000\\u0000",
            "\\u005c\\u0000 | \\\\u0000",
            "\\u005c\\u005c\\u0000 | \\\\\\u0000",
            "\\\\u0000 | \\\\u0000"
    })
    void preservesVisibleNulSpellingAndPrecedingBackslashes(String source, String expected) {
        assertEscapedComment("/** Escaped NUL: " + source + " */ class T {}", "Escaped NUL: " + expected);
    }

    private static void assertEscapedComment(String input, String expected) {
        Formatter singlePass = new Formatter(CONFIG, true);
        String firstPass = singlePass.format(input);
        assertThat(firstPass).doesNotContain("\0").contains(expected);
        assertThat(singlePass.format(firstPass)).isEqualTo(firstPass);

        Formatter formatter = new Formatter(CONFIG);
        String output = formatter.format(input);
        assertThat(output).doesNotContain("\0").contains(expected);
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void javadocCodePreservesEscapedNul() {
        String input = """
                class NulComment {
                  /**
                   * Escaped NUL: {@code \\u0000}
                   */
                  int value;
                }
                """;
        Formatter formatter = new Formatter(CONFIG);

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output.chars().filter(value -> value == 0).count())
                .as("Number of raw NUL characters")
                .isZero();
        assertThat(output).contains("\\u0000");
    }
}
