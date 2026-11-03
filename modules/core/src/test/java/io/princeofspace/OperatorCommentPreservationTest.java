package io.princeofspace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorCommentPreservationTest {
    private static final List<String> INPUTS = List.of(
            """
            class T { Object f() { return context == null
                // parent context
                // repeated
                // repeated
                ? channel().attribute(KEY).get() : context.tracingContext(); } }
            """,
            """
            class T { Object f() { return (path == null || path.length() == 1) // root path
                ? path : path.substring(1); } }
            """,
            """
            class T { boolean f() { return enabled() && span.version() <= 0 // unpublished
                && span.duration() > 0; } }
            """,
            """
            class T { void f() { if (start <= end) // still valid
                { accept(); }
                if (when >= zone - 1) // still valid
                { accept(); } } }
            """,
            """
            class T { Object f() { return ready() /* condition */
                ? // true branch
                first().get() // true suffix
                : /* false branch */ second().get(); } }
            """,
            """
            class T { boolean f() { return first() /* operand */ && second()
                // third operand
                && (ready() ? // choose
                    third().get() : fourth().get()); } }
            """
            ,
            """
            class T { void f() { if (start <= end)
                /* valid */ // repeated
                // repeated
                { accept(); } else if (ready()) /* inline */ { finish(); } } }
            """
    );

    @Test
    void retainsOperatorGapsOnTheFirstPassAndAtTheFixedPoint() {
        for (WrapStyle wrap : WrapStyle.values()) {
            for (IndentStyle indent : IndentStyle.values()) {
                for (boolean closingParen : List.of(false, true)) {
                    var config = FormatterConfig.builder().wrapStyle(wrap).indentStyle(indent)
                            .closingParenOnNewLine(closingParen).lineLength(80).build();
                    var singlePass = new Formatter(config, true);
                    var formatter = new Formatter(config);
                    for (String input : INPUTS) {
                        String once = singlePass.format(input);
                        String output = formatter.format(input);
                        assertThat(gaps(once)).as("first pass %s %s", wrap, input).isEqualTo(gaps(input));
                        assertThat(gaps(output)).as("fixed point %s %s", wrap, input).isEqualTo(gaps(input));
                        assertThat(formatter.format(output)).isEqualTo(output);
                        assertThat(output.lines().noneMatch(line -> !line.equals(line.stripTrailing()))).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void insertsRequiredBracesAfterTheConditionSuffix() {
        String input = "class T { void f() { if (ready()) // marker\n accept(); else finish(); } }";
        Formatter formatter = new Formatter(FormatterConfig.defaults());
        String output = formatter.format(input);
        assertThat(output).containsPattern("if \\(ready\\(\\)\\) // marker\\R\\h*\\{");
        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(new Formatter(FormatterConfig.defaults(), true).format(input))
                .containsPattern("if \\(ready\\(\\)\\) // marker\\R\\h*\\{");
    }

    @Test
    void insertsRequiredBracesAfterTheBlockConditionSuffix() {
        String input = "class T { void f() { if (ready()) /* marker */ accept(); else finish(); } }";
        for (WrapStyle wrap : WrapStyle.values()) {
            var config = FormatterConfig.builder().wrapStyle(wrap).build();
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                String output = formatter.format(input);
                assertThat(gaps(output)).containsExactly(new Gap(")", "/* marker */", "{", true));
            }
            Formatter formatter = new Formatter(config);
            String output = formatter.format(input);
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    private record Gap(String left, String comment, String right, boolean trailing) {}

    private static List<Gap> gaps(String source) {
        var unit = new JavaParser().parse(source).getResult().orElseThrow();
        List<JavaToken> tokens = new ArrayList<>();
        unit.getTokenRange().orElseThrow().forEach(tokens::add);
        List<Gap> gaps = new ArrayList<>();
        JavaToken previous = null;
        for (int i = 0; i < tokens.size(); i++) {
            JavaToken token = tokens.get(i);
            if (token.getCategory().isComment()) {
                JavaToken next = null;
                for (int j = i + 1; j < tokens.size(); j++) {
                    if (!tokens.get(j).getCategory().isWhitespaceOrComment()) {
                        next = tokens.get(j);
                        break;
                    }
                }
                gaps.add(new Gap(previous == null ? "" : previous.getText(), token.getText().stripTrailing(),
                        next == null ? "" : next.getText(), previous != null
                                && previous.getRange().orElseThrow().end.line == token.getRange().orElseThrow().begin.line));
            } else if (!token.getCategory().isWhitespaceOrComment()) {
                previous = token;
            }
        }
        return gaps;
    }
}
