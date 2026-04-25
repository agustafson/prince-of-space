package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * High-level “macro shape” checks that {@link WrapStyle} means the same thing across list-like
 * constructs: {@code WIDE} packs greedily, {@code BALANCED} and {@code NARROW} go tall when
 * wrapping (one element per continuation line) for the same long argument list.
 */
class RuleUniformityTest {

    @Test
    void widePacksLongArgumentListTighterThanBalanced() {
        String input =
                """
                class T {
                    void m() {
                        f(a, b, c, d, e, f, g, h, i, j);
                    }
                    void f(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {}
                }
                """;
        Formatter wide =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(false)
                                .build());
        Formatter balanced =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String wideOut = wide.format(input);
        String balOut = balanced.format(input);
        assertThat(balOut.lines().count())
                .as("BALANCED one-arg-per-line when wrapped should use more lines than WIDE")
                .isGreaterThan(wideOut.lines().count());
        assertThat(wide.format(wideOut)).isEqualTo(wideOut);
        assertThat(balanced.format(balOut)).isEqualTo(balOut);
    }

    @Test
    void narrowMatchesBalancedTallShapeForMethodArguments() {
        String input =
                """
                class T {
                    void m() {
                        f(a, b, c, d, e, f, g, h, i, j);
                    }
                    void f(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {}
                }
                """;
        Formatter narrow =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.NARROW)
                                .closingParenOnNewLine(false)
                                .build());
        Formatter balanced =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String n = narrow.format(input);
        String b = balanced.format(input);
        assertThat(n.lines().count())
                .as("NARROW and BALANCED both use one-arg-per-line when args wrap")
                .isEqualTo(b.lines().count());
        assertThat(narrow.format(n)).isEqualTo(n);
    }
}
