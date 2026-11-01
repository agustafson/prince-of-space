package io.princeofspace;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

class SurrogateCharLiteralReproducerTest {

    private static final FormatterConfig CONFIG = FormatterConfig.builder()
            .indentSize(2)
            .lineLength(60)
            .javaLanguageLevel(JavaLanguageLevel.of(25))
            .build();

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("charSpellings")
    void charValuesSurviveUtf8Encoding(String spelling, int expected)
            throws CharacterCodingException {
        for (String keyword : List.of("class", "cl\\u0061ss")) {
            String input = keyword + " T { char value = '" + spelling + "'; }";
            for (boolean singlePass : new boolean[] {false, true}) {
                Formatter formatter = new Formatter(CONFIG, singlePass);
                String output = formatter.format(input);
                String persisted = encodeAsUtf8(output);

                assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                        .extracting(literal -> (int) literal.asChar())
                        .containsExactly(expected);
                if (expected == 'é' || expected == '中') {
                    assertThat(output).contains("'" + (char) expected + "'");
                }
                if (!singlePass) {
                    assertThat(formatter.format(output)).isEqualTo(output);
                }
            }
        }
    }

    static Stream<Arguments> charSpellings() {
        List<Arguments> cases = new ArrayList<>();
        for (char value : new char[] {'A', 'é', '中', '\u0301', '\uD7FF', '\uD800', '\uDBFF',
                '\uDC00', '\uDFFF', '\uE000', '\uFDD0', '\uFFFE', '\uFFFF', '\uFEFF', '\u200D', '\u202E'}) {
            cases.add(Arguments.of(String.valueOf(value), (int) value));
            cases.add(Arguments.of(unicodeEscapes(String.valueOf(value)), (int) value));
        }
        cases.addAll(List.of(
                Arguments.of("\\n", 10), Arguments.of("\\r", 13), Arguments.of("\\t", 9),
                Arguments.of("\\b", 8), Arguments.of("\\f", 12), Arguments.of("\\'", 39),
                Arguments.of("\\\"", 34), Arguments.of("\\\\", 92), Arguments.of("\\0", 0),
                Arguments.of("\\141", 97), Arguments.of("\\377", 255),
                Arguments.of("\\uuuu0041", 65), Arguments.of("\\u005c\\u005c", 92)));
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("stringSpellings")
    void shortAndWrappedStringValuesSurviveUtf8Encoding(String spelling, String value)
            throws CharacterCodingException {
        for (int repeats : new int[] {1, 30}) {
            boolean wrapped = repeats > 1;
            for (String keyword : List.of("class", "cl\\u0061ss")) {
                String input = keyword + " T { String value = \"" + ("word " + spelling).repeat(repeats) + "\"; }";
                String expected = ("word " + value).repeat(repeats);
                for (boolean singlePass : new boolean[] {false, true}) {
                    Formatter formatter = new Formatter(CONFIG, singlePass);
                    String output = formatter.format(input);
                    String persisted = encodeAsUtf8(output);

                    assertThat(StaticJavaParser.parse(persisted).findAll(StringLiteralExpr.class).stream()
                            .map(StringLiteralExpr::asString).collect(joining())).isEqualTo(expected);
                    if (wrapped) {
                        assertThat(output).contains("+ ");
                    }
                    assertThat(output.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(60));
                    for (String unicode : List.of("é", "中", "😀")) {
                        if (value.contains(unicode)) {
                            assertThat(output).contains(unicode);
                        }
                    }
                    if (!singlePass) {
                        assertThat(formatter.format(output)).isEqualTo(output);
                    }
                }
            }
        }
    }

    static Stream<Arguments> stringSpellings() {
        List<Arguments> cases = new ArrayList<>();
        String highStart = Character.toString(0xD800);
        String highEnd = Character.toString(0xDBFF);
        String lowStart = Character.toString(0xDC00);
        String lowEnd = Character.toString(0xDFFF);
        for (String value : List.of("Aé中\u0301", "\uD7FF\uE000\uFDD0\uFFFE\uFFFF", highStart, lowEnd,
                highStart + lowStart, highEnd + lowEnd, "😀", lowStart + highStart, highStart + highEnd,
                lowStart + lowEnd, highStart + "x" + lowStart, highStart + highStart + lowStart + lowStart)) {
            cases.add(Arguments.of(value, value));
            cases.add(Arguments.of(unicodeEscapes(value), value));
        }
        cases.add(Arguments.of("\\\"\\'\\\\\\n\\r\\t\\b\\f\\0\\141\\377", "\"'\\\n\r\t\b\f\0a\u00FF"));
        cases.add(Arguments.of("\\\\uD800", "\\uD800"));
        cases.add(Arguments.of("\\u005c\\u005c\\u005c\\u006e", "\\\n"));
        cases.add(Arguments.of(Character.toString(0xD83D) + "\\uDE00", "😀"));
        cases.add(Arguments.of("\\uD83D" + Character.toString(0xDE00), "😀"));
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("invalidCharSpellings")
    void rejectsInvalidCharLiterals(String spelling) {
        String input = "class T { char value = '" + spelling + "'; }";
        assertThat(new Formatter(CONFIG).formatResult(input)).isInstanceOf(FormatResult.ParseFailure.class);
    }

    static Stream<String> invalidCharSpellings() {
        return Stream.of("😀", "\\uD83D\\uDE00", "\\u000A", "\\u000D", "\\u0027");
    }

    private static String unicodeEscapes(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            escaped.append("\\u").append(HexFormat.of().withUpperCase().toHexDigits(value.charAt(i)));
        }
        return escaped.toString();
    }

    private static String encodeAsUtf8(String output) throws CharacterCodingException {
        var encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(output));
        String decoded = StandardCharsets.UTF_8.newDecoder().decode(encoded).toString();
        assertThat(decoded).isEqualTo(output);
        return decoded;
    }

    @Test
    void surrogateCharValuesSurviveSavingAsUtf8() throws CharacterCodingException {
        String input = "class Surrogates { char high = '\\uD801'; char low = '\\uDC01'; }";
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .indentSize(2)
                .lineLength(100)
                .javaLanguageLevel(JavaLanguageLevel.of(25))
                .build());

        String output = formatter.format(input);
        String persisted = encodeAsUtf8(output);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                .as("UTF-16 code units after a UTF-8 round trip")
                .extracting(literal -> (int) literal.asChar())
                .containsExactly(0xD801, 0xDC01);
    }

    @Test
    void escapedSurrogateWidthsAndCommentsArePreserved() throws CharacterCodingException {
        String input = "class Chars { void test() { consume('\\uD800', /* middle */ '\\uDC00', "
                + "'\\uDBFF', '\\uDFFF'); } }";
        Formatter formatter = new Formatter(FormatterConfig.builder().lineLength(50).build());

        String output = formatter.format(input);

        assertThat(output).contains("/* middle */");
        assertThat(output.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(50));
        assertThat(formatter.format(output)).isEqualTo(output);
        String persisted = encodeAsUtf8(output);
        assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                .extracting(literal -> (int) literal.asChar())
                .containsExactly(0xD800, 0xDC00, 0xDBFF, 0xDFFF);
    }
}
