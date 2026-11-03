package io.princeofspace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementEndingCommentPreservationTest {
    private static final List<String> INPUTS = List.of(
            """
            class T {
                boolean f(boolean enabled, Object x) {
                    return enabled && /* guard */ check(x, // keep
                        false) // suffix
                        ;
                }
            }
            """,
            """
            class T {
                String f(Object x) {
                    return "p" + /* label */ String.format("%s", // keep
                        x) // suffix
                        ;
                }
            }
            """,
            """
            class T {
                void f(Object x) {
                    sink(check(x, // keep
                        false)) // suffix
                        ;
                }
            }
            """,
            """
            class T {
                static Object value = check(null, // keep
                    false) // suffix
                    ;
            }
            """,
            """
            class T {
                void f(Object x) {
                    Object value = check(x, // keep
                        false) // suffix
                        ;
                }
            }
            """,
            """
            class T {
                void f(Object x) {
                    throw failure(check(x, // keep
                        false)) // suffix
                        ;
                }
            }
            """,
            """
            class T {
                boolean f(boolean enabled, Object x) {
                    return enabled && /* guard */ check(x, // repeated
                        false) // repeated
                        /* between */ // repeated
                        ;
                }
            }
            """,
            """
            class T {
                Object first = check(null), second = check(null, // keep
                    false) /* ending */ // suffix
                    ;
            }
            """,
            """
            class T {
                Object f(Object x) {
                    return check(x, // keep
                        false) /* suffix */;
                }
                void g() { return // suffix
                    ; }
            }
            """
    );

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void endingCommentsStayOutsideExpressionsBeforeTheSemicolon(WrapStyle wrap) {
        for (int width : new int[] {50, 100}) {
            for (IndentStyle indent : IndentStyle.values()) {
                for (boolean closingParen : new boolean[] {false, true}) {
                    FormatterConfig config = FormatterConfig.builder().wrapStyle(wrap).lineLength(width)
                            .indentStyle(indent).closingParenOnNewLine(closingParen).build();
                    Formatter firstPass = new Formatter(config, true);
                    Formatter formatter = new Formatter(config);
                    for (String newline : new String[] {"\n", "\r\n"}) {
                        for (String fixture : INPUTS) {
                            String input = fixture.replace("\n", newline);
                            List<Boundary> expected = boundaries(input);
                            String once = firstPass.format(input);
                            assertThat(boundaries(once)).as("first pass %s/%s/%s:%n%s", wrap, indent, width, input)
                                    .containsExactlyElementsOf(expected);
                            String output = formatter.format(input);
                            assertThat(boundaries(output)).as("fixed point %s/%s/%s:%n%s", wrap, indent, width, input)
                                    .containsExactlyElementsOf(expected);
                            assertThat(formatter.format(output)).isEqualTo(output);
                            assertThat(output.lines()).noneMatch(line -> line.endsWith(" ") || line.endsWith("\t"));
                        }
                    }
                }
            }
        }
    }

    private record Boundary(List<String> left, String comment, List<String> right, boolean endingTrailing) {}

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void ownedOperandCaptionKeepsItsNestedArgumentComment(WrapStyle wrap) {
        String input = "class T { boolean f() { return enabled &&\n // guard\n check(items, // keep\n false); } }";
        for (int width : new int[] {50, 100}) {
            FormatterConfig config = FormatterConfig.builder().wrapStyle(wrap).lineLength(width).build();
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                List<Boundary> output = boundaries(formatter.format(input));
                assertThat(output).extracting(Boundary::comment).containsExactly("// guard", "// keep");
                assertThat(output.get(0).right()).isIn(
                        List.of("check", "(", "items"), List.of("&&", "check", "("));
                assertThat(output.get(1)).isEqualTo(boundaries(input).get(1));
            }
            Formatter formatter = new Formatter(config);
            String output = formatter.format(input);
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void mixedEndingGroupRetainsItsInlineBlockWidth(WrapStyle wrap) {
        String input = "class T { String f() { return \"aaaaaaa\" + \"bbbbbbb\" /* label */ // suffix\n ; } }";
        FormatterConfig config = FormatterConfig.builder().wrapStyle(wrap).lineLength(40).build();
        for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
            String output = formatter.format(input);
            assertThat(boundaries(output)).containsExactlyElementsOf(boundaries(input));
            assertThat(output.lines().map(line -> line.split("//", 2)[0].stripTrailing()))
                    .allMatch(line -> line.length() <= 40);
        }
        Formatter formatter = new Formatter(config);
        String output = formatter.format(input);
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    private static List<Boundary> boundaries(String source) {
        var parsed = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)).parse(source);
        assertThat(parsed.isSuccessful()).as("%s", parsed.getProblems()).isTrue();
        List<Boundary> result = new ArrayList<>();
        for (JavaToken token : parsed.getResult().orElseThrow().getTokenRange().orElseThrow()) {
            if (!token.getCategory().isComment()) {
                continue;
            }
            List<String> left = codeWindow(token, true);
            List<String> right = codeWindow(token, false);
            JavaToken previous = token.getPreviousToken().orElseThrow();
            while (previous.getCategory().isWhitespaceOrComment()) {
                previous = previous.getPreviousToken().orElseThrow();
            }
            boolean trailing = right.get(0).equals(";")
                    && previous.getRange().orElseThrow().end.line == token.getRange().orElseThrow().begin.line;
            result.add(new Boundary(left, token.getText(), right, trailing));
        }
        return result;
    }

    private static List<String> codeWindow(JavaToken comment, boolean before) {
        List<String> tokens = new ArrayList<>();
        var token = before ? comment.getPreviousToken() : comment.getNextToken();
        while (token.isPresent() && tokens.size() < 3) {
            JavaToken current = token.orElseThrow();
            if (!current.getCategory().isWhitespaceOrComment()) {
                if (before) {
                    tokens.add(0, current.getText());
                } else {
                    tokens.add(current.getText());
                }
            }
            token = before ? current.getPreviousToken() : current.getNextToken();
        }
        return tokens;
    }
}
