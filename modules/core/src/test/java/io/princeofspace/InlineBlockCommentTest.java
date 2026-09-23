package io.princeofspace;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InlineBlockCommentTest {
    static Stream<String> inlineComments() {
        return Stream.of(
                "configure(null /* serviceName */, 123);",
                "configure(/* serviceName */ null, 123);",
                "Object value = /* unset */ null;",
                "return /* unavailable */ null;",
                "new Client(null /* serviceName */, 123);",
                "super(null /* serviceName */, 123);",
                "Object[] values = {null /* serviceName */, 123};",
                "Object value = null /* unset */;",
                "Object value /* optional */ = null;",
                "configure() /* side effect */;",
                "return null /* unavailable */;",
                "throw new Exception() /* unavailable */;",
                "String /* domain */ serviceName = null;",
                "receiver./* operation */ configure();",
                "receiver./* first */ configure()./* second */ finish();",
                "configure(null /* serviceName */ /* optional */, 123);",
                "configure(null /* repeated */, null /* repeated */, 123);",
                "Object[] values = {null /* last value */};",
                "String first = null /* first */, second = null /* second */;",
                "configure(null /* serviceName */);",
                "configure(123, null /* serviceName */);");
    }

    @ParameterizedTest
    @MethodSource("inlineComments")
    void preservesInlineTokenBoundaries(String statement) {
        Formatter formatter = new Formatter(FormatterConfig.builder().indentSize(2).lineLength(100).build());
        String input = "class T { Object m() { " + statement + " } }";
        String output = formatter.format(input);
        assertThat(output).contains(statement);
        assertThat(tokens(output)).containsExactlyElementsOf(tokens(input));
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    private static List<String> tokens(String source) {
        List<String> result = new ArrayList<>();
        for (JavaToken token : StaticJavaParser.parse(source).getTokenRange().orElseThrow()) {
            if (!token.getCategory().isWhitespace()) {
                result.add(token.getText());
            }
        }
        return result;
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void lastArgumentCommentStaysInlineWhenCloserWasOnNextLine(WrapStyle style) {
        Formatter formatter = new Formatter(FormatterConfig.builder().wrapStyle(style).build());
        String input = """
                class T {
                  void m() {
                    configure(
                        null /* serviceName */
                    );
                  }
                }
                """;
        String output = formatter.format(input);
        assertThat(output).contains("null /* serviceName */");
        assertThat(tokens(output)).containsExactlyElementsOf(tokens(input));
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void inlineCommentWidthParticipatesInWrapping() {
        Formatter formatter = new Formatter(FormatterConfig.builder().indentSize(2).lineLength(60).build());
        String output = formatter.format("""
                class T {
                  void m() {
                    configure(null /* service name supplied by caller */, 123, 456);
                  }
                }
                """);
        assertThat(output).contains("null /* service name supplied by caller */,\n");
        assertThat(output.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(60));
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void argumentCommentStaysBeforeComma(WrapStyle style) {
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .indentSize(2).lineLength(100).wrapStyle(style).build());
        String input = """
                class T {
                  void m() {
                    configure(null /* serviceName */,
                        "operationNameThatIsLongEnoughToForceThisArgumentListToWrapAcrossMultipleLines",
                        123);
                  }
                }
                """;
        String output = formatter.format(input);
        assertThat(output).contains("null /* serviceName */,");
        assertThat(formatter.format(output)).isEqualTo(output);
    }
}
