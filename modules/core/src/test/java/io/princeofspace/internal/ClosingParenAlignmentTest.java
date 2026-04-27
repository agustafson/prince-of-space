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
    void singleNestedCall_closingParenOnNewLineTrue_breaksBeforeWrappedArgument() {
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

        assertThat(output).contains("methodA(\n");
        assertThat(output).contains("\n                methodB(\n");
        assertThat(output).contains("\n                )\n        );\n");
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

    @Test
    void ternaryNestedWrappedCalls_indentArgumentsPastContinuationColumn() {
        Formatter formatter = formatter(true);
        String input =
                """
                class C {
                    Object m() {
                        return methodA(longConditionExpressionForCheck ? methodB(longArg1, longArg2, longArg3, longArg4, longArg5, longArg6) : methodC(longArg1, longArg2, longArg3, longArg4, longArg5, longArg6));
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("\n                ? methodB(\n");
        assertThat(output).contains("\n                        longArg1,\n");
        assertThat(output).contains("\n                : methodC(\n");
        assertThat(output).contains("\n                        longArg6");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void singleWrappedTernaryArg_breaksBeforeArgumentWhenNotChainReceiver() {
        Formatter formatter = formatter(true);
        String input =
                """
                class C {
                    Object m() {
                        return methodA(longConditionExpressionForCheck ? methodB(longArg1, longArg2, longArg3, longArg4, longArg5, longArg6) : methodC(longArg1, longArg2, longArg3, longArg4, longArg5, longArg6));
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("return methodA(\n");
        assertThat(output).contains("\n                longConditionExpressionForCheck\n");
        assertThat(output).contains("\n                )\n        );\n");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void singleWrappedChainReceiverArg_staysInlineWithOuterCall() {
        Formatter formatter = formatter(true);
        String input =
                """
                class C {
                    Object m() {
                        return methodA(longPrefixValueBuilderForFormattedChain().formatted(longArg1, longArg2, longArg3, longArg4, longArg5));
                    }

                    String longPrefixValueBuilderForFormattedChain() {
                        return "prefix-value";
                    }
                }
                """;

        String output = formatter.format(input);

        assertThat(output).contains("return methodA(longPrefixValueBuilderForFormattedChain()\n");
        assertThat(output).doesNotContain("return methodA(\n");
        assertThat(formatter.format(output)).isEqualTo(output);
    }
}
