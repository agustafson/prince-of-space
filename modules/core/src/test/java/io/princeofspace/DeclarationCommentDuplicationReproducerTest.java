package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reduced from dd-trace-java field initializers, local initializers and Hazelcast muzzle checks. */
class DeclarationCommentDuplicationReproducerTest {

    private final Formatter formatter = new Formatter(FormatterConfig.builder()
            .indentSize(2)
            .lineLength(100)
            .javaLanguageLevel(JavaLanguageLevel.of(25))
            .build());

    @Test
    void trailingCommentOnConstructorFieldIsPrintedOnce() {
        String input = """
                class T {
                  static final Object VALUE =
                      new Object(); // field marker
                  int following;
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// field marker");
    }

    @Test
    void trailingCommentOnLiteralFieldIsPrintedOnce() {
        String input = """
                class T {
                  static final long LIMIT =
                      64L; // limit marker
                  int following;
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// limit marker");
    }

    @Test
    void trailingCommentOnLocalInitializerIsPrintedOnce() {
        String input = """
                class T {
                  void f() {
                    Object value =
                        new Object(); // local marker
                  }
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// local marker");
    }

    @Test
    void leadingParameterCommentIsPrintedOnce() {
        String input = """
                class T {
                  void f(
                      // parameter marker
                      Object value) {}
                }
                """;

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// parameter marker");
    }
}
