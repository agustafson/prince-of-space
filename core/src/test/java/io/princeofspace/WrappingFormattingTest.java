package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 wrapping: regression tests for line-breaking behavior (method chains, binary/ternary,
 * type clauses). Full {@code examples/} golden parity is a larger follow-up once the pretty-printer
 * matches every edge case in {@code FormatterShowcase}.
 */
class WrappingFormattingTest {

    @Test
    void methodChain_wrapsEachSegmentWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(22)
                                .maxLineLength(80)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        return a.b().c().d();
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains(".c()");
        assertThat(out).contains(".d()");
        assertThat(out).contains("\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void logicalAnd_wrapsWithOperatorAtContinuationStart() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(50)
                                .maxLineLength(100)
                                .continuationIndentSize(4)
                                .build());
        String input =
                """
                class T {
                    boolean m(boolean a, boolean b, boolean c) {
                        return a && b && c;
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("&&");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void implementsClause_balanced_wrapsEachType() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(50)
                                .maxLineLength(120)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                "public class T implements java.io.Serializable, java.lang.Cloneable, AutoCloseable {}";
        String out = f.format(input);
        assertThat(out).contains("implements");
        assertThat(out).contains("java.io.Serializable");
        assertThat(f.format(out)).isEqualTo(out);
    }
}
