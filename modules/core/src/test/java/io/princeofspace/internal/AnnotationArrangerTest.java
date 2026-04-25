package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AnnotationArranger} is part of the transform pipeline; end-to-end layout for annotations is
 * primarily determined by the pretty printer. These tests document the required shapes from
 * {@code docs/formatting-rules.md} and guard regressions.
 */
class AnnotationArrangerTest {

    @Test
    void declarationAnnotations_eachOnOwnLine() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out =
                f.format(
                        "@Deprecated @SuppressWarnings(\"all\") public class T { public T() {} }");
        assertThat(out).contains("@Deprecated");
        assertThat(out).contains("@SuppressWarnings(\"all\")");
        assertThat(out.indexOf("@Deprecated"))
                .isLessThan(out.indexOf("@SuppressWarnings"));
        assertThat(out).containsPattern("@Deprecated\\R");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeUseAnnotations_stayInline() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out =
                f.format("import java.util.List; class T { void m(List<@org.eclipse.jdt.annotation.NonNull String> p) {} }");
        assertThat(out).contains("List<@org.eclipse.jdt.annotation.NonNull String>");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void mixed_declAndTypeUse_keepRespectiveStyles() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String in =
                """
                class T {
                    @Deprecated
                    @SuppressWarnings("all")
                    java.lang.@org.eclipse.jdt.annotation.NonNull String name = "x";
                }
                """;
        String out = f.format(in);
        assertThat(out).contains("java.lang.@org.eclipse.jdt.annotation.NonNull String");
        assertThat(out).contains("@Deprecated");
        assertThat(f.format(out)).isEqualTo(out);
    }
}
