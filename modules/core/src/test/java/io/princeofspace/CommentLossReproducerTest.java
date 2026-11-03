package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reduced from dd-trace-java Spring, Rediscala and debugger comment-loss cases. */
class CommentLossReproducerTest {

    private final Formatter formatter = new Formatter(FormatterConfig.builder()
            .indentSize(2)
            .lineLength(100)
            .javaLanguageLevel(JavaLanguageLevel.of(25))
            .build());

    @Test
    void annotationMemberValueKeepsItsInlineComment() {
        String input = """
                @interface Opt { String value(); }
                class T {
                  void f(@Opt(value = "a" /* annotation marker */) String value) {}
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("/* annotation marker */");
    }

    @Test
    void nestedCallKeepsCommentAfterOpeningParenthesis() {
        String input = """
                class T {
                  Object f() {
                    return startsWith("prefix")
                        .and(implementsInterface(
                            namedOneOf( // call marker
                                "a", "b")));
                  }
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// call marker");
    }

    @Test
    void chainedCallKeepsCommentBeforeSemicolon() {
        String input = """
                class T {
                  void f() {
                    builder.with(new Object())
                        .finish()
                    // .installOn(target)
                    ;
                  }
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// .installOn(target)");
    }
}
