package io.princeofspace.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnicodeEscapesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("translations")
    void followsJlsEligibility(String name, String source, String expected) throws Exception {
        assertThat(UnicodeEscapes.decode(source)).isEqualTo(expected);
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals") // Keep each source spelling visible beside its expected translation.
    static Stream<Arguments> translations() {
        String slash = "\\";
        String escapedSlash = "\\u005C";
        return Stream.of(
                Arguments.of("ordinary escape", "before\\u0041after", "beforeAafter"),
                Arguments.of("repeated u", "\\uuuu0041", "A"),
                Arguments.of("mixed-case hex", "\\u00aF", "\u00AF"),
                Arguments.of("even raw run", slash.repeat(2) + "u006e", slash.repeat(2) + "u006e"),
                Arguments.of("odd raw run", slash.repeat(3) + "u006e", slash.repeat(2) + "n"),
                Arguments.of("escaped backslash", escapedSlash + slash + "u006e", slash + "n"),
                Arguments.of("escaped then two raw backslashes", escapedSlash + slash.repeat(2) + "u006e",
                        slash.repeat(2) + "n"),
                Arguments.of("escaped then three raw backslashes", escapedSlash + slash.repeat(3) + "u006e",
                        slash.repeat(4) + "u006e"),
                Arguments.of("escaped then four raw backslashes", escapedSlash + slash.repeat(4) + "u006e",
                        slash.repeat(4) + "n"),
                Arguments.of("two escaped then two raw backslashes", escapedSlash.repeat(2) + slash.repeat(2) + "u006e",
                        slash.repeat(4) + "u006e"),
                Arguments.of("two escaped then three raw backslashes", escapedSlash.repeat(2) + slash.repeat(3) + "u006e",
                        slash.repeat(4) + "n"),
                Arguments.of("produced backslash is not rescanned", escapedSlash + "u0041", slash + "u0041"),
                Arguments.of("produced u is not rescanned", slash.repeat(2) + "\\u00750041", slash.repeat(2) + "u0041"),
                Arguments.of("logical line endings", "a\\u000Db\\u000Ac", "a\rb\nc"),
                Arguments.of("isolated surrogate has no lookahead", "\\uD800" + slash.repeat(2) + "u000A",
                        Character.toString(0xD800) + slash.repeat(2) + "u000A"),
                Arguments.of("surrogate pair", "\\uD83D\\uDE00", "😀"),
                Arguments.of("trailing backslash", "\\u0041\\", "A\\"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "class T {}", "\\", "\\\\u0041", "\\\\uZZZZ"})
    void retainsSourceWhenNoEscapeIsEligible(String source) throws Exception {
        assertThat(UnicodeEscapes.decode(source)).isSameAs(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u", "\\uuuu", "\\u0", "\\u00", "\\u000", "\\u000g", "\\u-001", "\\u００４１"})
    void rejectsMalformedEligibleEscapes(String source) {
        assertThatThrownBy(() -> UnicodeEscapes.decode("abc" + source))
                .isInstanceOf(UnicodeEscapes.MalformedEscapeException.class)
                .hasMessage("Invalid Unicode escape at source offset 3");
    }

    @Test
    void consumesExactlyFourHexDigits() throws Exception {
        assertThat(UnicodeEscapes.decode("\\u00412")).isEqualTo("A2");
    }
}
