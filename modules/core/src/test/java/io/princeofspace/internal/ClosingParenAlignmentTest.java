package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClosingParenAlignmentTest {
    private static Formatter formatter(boolean closingParenOnNewLine) {
        return new Formatter(
                FormatterConfig.builder()
                        .wrapStyle(WrapStyle.BALANCED)
                        .closingParenOnNewLine(closingParenOnNewLine)
                        .lineLength(80)
                        .indentSize(4)
                        .javaLanguageLevel(JavaLanguageLevel.of(17))
                        .build());
    }

    @Test
    void colineNestedCalls_closingParenOnNewLineTrue_compactsToAlignedCloserRun() {
        Formatter formatter = formatter(true);
        String input =
                """
                class C {
                    void m() {
                        methodA(methodB(aaaaaaa, bbbbbbb, ccccccc, ddddddd, eeeeeee, fffffff, ggggggg));
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("\n        ));\n");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void lineSeparatedNestedCalls_closingParenOnNewLineTrue_keepsSeparateAlignedClosers() {
        Formatter formatter = formatter(true);
        String input =
                """
                class C {
                    void m() {
                        methodA(
                                /* keep line-separated nesting */
                                methodB(aaaaaaa, bbbbbbb, ccccccc, ddddddd, eeeeeee, fffffff, ggggggg));
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("\n                )\n        );\n");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void lineSeparatedNestedCalls_closingParenOnNewLineFalse_compactsInlineClosers() {
        Formatter formatter = formatter(false);
        String input =
                """
                class C {
                    void m() {
                        methodA(
                                methodB(aaaaaaa, bbbbbbb, ccccccc, ddddddd, eeeeeee, fffffff, ggggggg));
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("methodB("); // sanity no accidental format collapse
        assertThat(output).contains("ggggggg));");
        assertThat(formatter.format(output)).isEqualTo(output);
    }
}
