package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reduced from dd-trace-java CrashUploader and Jetty matcher comment-order failures. */
class CommentOrderReproducerTest {

    private final Formatter formatter = new Formatter(FormatterConfig.builder()
            .indentSize(2)
            .lineLength(100)
            .javaLanguageLevel(JavaLanguageLevel.of(25))
            .build());

    @Test
    void argumentCommentsRetainBlockThenLineOrder() {
        String input = """
                class T {
                  void f() {
                    call(
                        true, /* retry */
                        4, /* max */ // explanation
                        "host");
                  }
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("/* retry */")
                .containsOnlyOnce("/* max */")
                .containsOnlyOnce("// explanation");
        assertThat(output.indexOf("/* retry */")).isLessThan(output.indexOf("/* max */"));
        assertThat(output.indexOf("/* max */"))
                .as("Block comment before line comment in%n%s", output)
                .isLessThan(output.indexOf("// explanation"));
    }

    @Test
    void consecutiveLineCommentsKeepTheirOrder() {
        String input = """
                class T {
                  Object f() {
                    return first().or(
                        // first marker
                        // second marker
                        // third marker
                        second().and(third()));
                  }
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// first marker")
                .containsOnlyOnce("// second marker")
                .containsOnlyOnce("// third marker");
        assertThat(output.indexOf("// first marker")).isLessThan(output.indexOf("// second marker"));
        assertThat(output.indexOf("// second marker"))
                .as("Second comment before third comment in%n%s", output)
                .isLessThan(output.indexOf("// third marker"));
    }
}
