package io.princeofspace;

import com.github.javaparser.StaticJavaParser;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Phase 5: comments survive formatting and formatting is idempotent when comments are present. */
class CommentPreservationTest {

    private static final Formatter DEFAULT = new Formatter(FormatterConfig.defaults());

    @ParameterizedTest
    @ValueSource(strings = {"first", "\\" + "uuuu0041"})
    void javadocsStayWithTheirFields(String firstValue) {
        String input =
                """
                class T {
                    /**
                     * First field.
                     *        h
                     *       /|\\
                     *      / | \\
                     *     /  |  \\
                     *    d   e   g
                     *   /|\\      |
                     *  / | \\     |
                     * a  b  c    f
                     */
                    String first = "FIRST_VALUE";

                    /** Second field. */
                    String second = "second";

                    /** Third field. */
                    String third = "third";
                }
                """.replace("FIRST_VALUE", firstValue);

        String output = DEFAULT.format(input);

        var type = StaticJavaParser.parse(output).getType(0);
        assertThat(type.getFieldByName("second").orElseThrow().getJavadocComment().orElseThrow().getContent())
                .contains("Second field.");
        assertThat(type.getFieldByName("third").orElseThrow().getJavadocComment().orElseThrow().getContent())
                .contains("Third field.");
        assertThat(DEFAULT.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void consecutiveLineCommentsBetweenChainCalls_preservedInOrder(WrapStyle style) {
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .wrapStyle(style)
                .javaLanguageLevel(JavaLanguageLevel.of(25))
                .build());
        String input = """
                class Repro {
                    Object configure(Builder b) {
                        return b
                                .first()
                                // Comment A, line one of two, explaining why the second call
                                // comes next: line two of two.
                                .second()
                                // Single-line comment B.
                                .third();
                    }
                }
                """;
        String expected = """
                class Repro {
                    Object configure(Builder b) {
                        return b
                            .first()
                            // Comment A, line one of two, explaining why the second call
                            // comes next: line two of two.
                            .second()
                            // Single-line comment B.
                            .third();
                    }
                }
                """;

        String out = formatter.format(input);

        assertThat(out).isEqualTo(expected);
        assertThatCode(() -> StaticJavaParser.parse(out)).doesNotThrowAnyException();
        assertThat(formatter.format(out)).isEqualTo(out);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void threeLineCommentGroupsBetweenChainCalls_preservedOnce(WrapStyle style) {
        Formatter formatter = new Formatter(FormatterConfig.builder().wrapStyle(style).build());
        String input = """
                class T {
                    Object configure(Builder b) {
                        return b.first()
                            // A1
                            // A2
                            // A3
                            .second()
                            // B1
                            // B2
                            // B3
                            .third();
                    }
                }
                """;

        String out = formatter.format(input);

        assertThat(out).contains(".first()\n            // A1\n            // A2\n            // A3\n            .second()");
        assertThat(out).contains(".second()\n            // B1\n            // B2\n            // B3\n            .third()");
        assertThat(out).containsOnlyOnce("// A1").containsOnlyOnce("// A2").containsOnlyOnce("// A3")
                .containsOnlyOnce("// B1").containsOnlyOnce("// B2").containsOnlyOnce("// B3");
        assertThat(formatter.format(out)).isEqualTo(out);
    }

    @Test
    void lineComment_beforeStatement_preserved() {
        String input =
                """
                class T {
                    void m() {
                        // explain
                        int x = 1;
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("// explain");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void blockComment_preserved() {
        String input =
                """
                class T {
                    void m() {
                        /*
                         * note
                         */
                        int x = 1;
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("/*");
        assertThat(out).contains("note");
        assertThat(out).contains("*/");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void javadocOnMethod_preserved() {
        String input =
                """
                class T {
                    /** Does work. */
                    void m() {}
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("/**");
        assertThat(out).contains("Does work.");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void endOfLineComment_preserved() {
        String input =
                """
                class T {
                    void m() {
                        int x = 1; // trailing
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("// trailing");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void trailingCommentOnWrappedBinaryExpression_preservedOnceAndConverges() {
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .indentSize(2)
                                .lineLength(100)
                                .javaLanguageLevel(JavaLanguageLevel.of(25))
                                .build());
        String comment = "// +1 to account for \"no value\" (omitted tag)";
        String input =
                """
                class Reproducer {
                  void update(int[] tagIdxMultipliers, int[][] tagValues, int i) {
                    tagIdxMultipliers[i] =
                        tagIdxMultipliers[i - 1]
                            * (tagValues[i - 1].length + 1); // +1 to account for "no value" (omitted tag)
                  }
                }
                """;
        String expected =
                """
                class Reproducer {
                  void update(int[] tagIdxMultipliers, int[][] tagValues, int i) {
                    // +1 to account for "no value" (omitted tag)
                    tagIdxMultipliers[i] = tagIdxMultipliers[i - 1] * (tagValues[i - 1].length + 1);
                  }
                }
                """;

        String out = formatter.format(input);

        assertThat(out).isEqualTo(expected).containsOnlyOnce(comment);
        assertThatCode(() -> StaticJavaParser.parse(out)).doesNotThrowAnyException();
        assertThat(formatter.format(out)).isEqualTo(out);
    }

    @Test
    void commentBetweenStatements_preserved() {
        String input =
                """
                class T {
                    void a() {
                        int x = 1;
                        // between
                        int y = 2;
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("// between");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void consecutiveLineCommentsBeforeField_remainAdjacent_noBlankLineBetween() {
        String input =
                """
                class T {
                    // header 1
                    // header 2
                    int x;
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("// header 1\n    // header 2");
        assertThat(out).doesNotContain("// header 1\n\n    // header 2");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void typeUseNullable_nextToType_preserved() {
        String input =
                """
                class T {
                    @org.jspecify.annotations.Nullable String s() {
                        return null;
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("@org.jspecify.annotations.Nullable");
        assertThat(out).contains("String s()");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void blockCommentBetweenArrayInitializerElements_staysInlineBeforeItsValue() {
        String input =
                """
                class T {
                    static final String[] X = {"alpha", /* sep */ "beta", "gamma"};
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("{\"alpha\", /* sep */ \"beta\", \"gamma\"}");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void lineCommentBetweenArrayInitializerElements_preservedOnNewLine() {
        String input =
                """
                class T {
                    static final String[] X = {
                        "alpha",
                        // separator
                        "beta",
                        "gamma"
                    };
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("// separator");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void leadingBlockCommentInArrayInitializer_preservesInternalBlankLines() {
        String input =
                """
                class T {
                    static final String[] ITEMS = {
                        /*
                         * first paragraph
                         *
                         * second paragraph
                         */
                        "alpha",
                        "beta"
                    };
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("first paragraph");
        assertThat(out).contains("second paragraph");
        // Internal blank line in the leading block comment must survive as a bare ` *` separator line.
        assertThat(out).containsPattern("\\* first paragraph\\R\\s*\\*\\R\\s*\\* second paragraph");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void ifWithCommentBetweenConditionAndStatement_isIdempotent() {
        String input =
                """
                class T {
                    void m(boolean x) {
                        if (x) /* guard */ return;
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(out).contains("/* guard */");
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }

    @Test
    void lambdaBlock_blankLineBetweenStatements_idempotentLikeMethodBody() {
        String input =
                """
                class T {
                    void m() {
                        java.lang.Runnable r =
                                () -> {
                                    int a = 1;

                                    int b = 2;
                                };
                    }
                }
                """;
        String out = DEFAULT.format(input);
        assertThat(DEFAULT.format(out)).isEqualTo(out);
    }
}
