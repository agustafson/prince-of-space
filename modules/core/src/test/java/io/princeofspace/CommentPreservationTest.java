package io.princeofspace;

import com.github.javaparser.StaticJavaParser;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Phase 5: comments survive formatting and formatting is idempotent when comments are present. */
class CommentPreservationTest {

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void inlineBlockCommentsInEmptyCalls_stayInsideParentheses(WrapStyle style) {
        FormatterConfig config = FormatterConfig.builder().wrapStyle(style).build();
        String input = """
                class T {
                    void m() {
                        f(/* c */);
                        receiver.first(/* first */).second(/* second */ /* last */);
                        g(
                            /* separate */
                        );
                        h(
                            // line comment
                        );
                    }
                }
                """;

        String singlePass = new Formatter(config, true).format(input);
        assertThat(singlePass).contains("f(/* c */)", ".first(/* first */)", ".second(/* second */ /* last */)");
        assertThat(singlePass).containsPattern("g\\(\\R\\h*/\\* separate \\*/\\R\\h*\\)");
        assertThat(singlePass).containsPattern("h\\(\\R\\h*// line comment\\R\\h*\\)");
        Formatter formatter = new Formatter(config);
        String output = formatter.format(input);
        assertThat(output).isEqualTo(singlePass);
        assertThat(formatter.format(output)).isEqualTo(output);
        assertThatCode(() -> StaticJavaParser.parse(output)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void trailingSoleArgumentComment_staysInsideChainedCall(WrapStyle style) {
        FormatterConfig config = FormatterConfig.builder().lineLength(120).wrapStyle(style).build();
        String call = ".transform(";
        for (String comment : new String[] {"// closing marker", "/* closing marker */"}) {
            String input = """
                    class T {
                        Object m() {
                            return receiver
                                .transform(value COMMENT
                                )
                                .finish();
                        }
                    }
                    """.replace("COMMENT", comment);

            String singlePass = new Formatter(config, true).format(input);
            assertThat(singlePass).containsOnlyOnce(comment);
            assertThat(singlePass.indexOf(comment)).isGreaterThan(singlePass.indexOf(call));
            assertThat(singlePass.indexOf(comment)).isLessThan(singlePass.indexOf(')', singlePass.indexOf(call)));

            Formatter formatter = new Formatter(config);
            String output = formatter.format(input);
            assertThat(output).containsOnlyOnce(comment);
            assertThat(output.indexOf(comment)).isGreaterThan(output.indexOf(call));
            assertThat(output.indexOf(comment)).isLessThan(output.indexOf(')', output.indexOf(call)));
            assertThatCode(() -> StaticJavaParser.parse(output)).doesNotThrowAnyException();
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void nestedEmptyArrayCommentsWithTabs_preserveFollowingMemberIndent(WrapStyle style) {
        FormatterConfig config = FormatterConfig.builder()
                .indentStyle(IndentStyle.TABS)
                .indentSize(1)
                .wrapStyle(style)
                .javaLanguageLevel(JavaLanguageLevel.of(25))
                .build();
        String input = """
                class T {
                    int[][] values = {{
                        // Empty row.
                        /* No elements. */
                    }};
                    int following = 1;
                    void after() {
                        int local = 2;
                    }
                }
                """;

        String output = new Formatter(config, true).format(input);

        assertThat(output).containsOnlyOnce("// Empty row.");
        assertThat(output).containsOnlyOnce("/* No elements. */");
        assertThat(output).contains("\n\tint following = 1;");
        assertThat(output).contains("\n\tvoid after() {\n\t\tint local = 2;\n\t}");
        String openingLine = output.lines().filter(line -> line.contains("int[][] values =")).findFirst().orElseThrow();
        String closingLine = output.lines().filter(line -> line.stripLeading().startsWith("}")).findFirst().orElseThrow();
        assertThat(closingLine.replace("\t", "    ").indexOf('}'))
                .isEqualTo(openingLine.replace("\t", "    ").lastIndexOf('{'));
        Formatter formatter = new Formatter(config);
        String stable = formatter.format(input);
        assertThat(stable).containsOnlyOnce("// Empty row.").containsOnlyOnce("/* No elements. */");
        assertThat(stable).contains("\n\tint following = 1;");
        assertThat(formatter.format(stable)).isEqualTo(stable);
    }

    @ParameterizedTest
    @CsvSource({"WIDE, SPACES", "NARROW, SPACES", "BALANCED, SPACES", "WIDE, TABS", "NARROW, TABS", "BALANCED, TABS"})
    void distinctCallAndArgumentComments_areBothPreservedAndAligned(WrapStyle style, IndentStyle indentStyle) {
        FormatterConfig config = FormatterConfig.builder()
                .lineLength(120)
                .wrapStyle(style)
                .indentStyle(indentStyle)
                .build();
        Formatter formatter = new Formatter(config);
        for (String[] comments : new String[][] {{"// CALL", "// ARG"}, {"/* CALL */", "/* ARG */"}}) {
            String input = """
                    class T {
                        Object m() {
                            return receiver
                                CALL_COMMENT
                                .first(
                                    ARG_COMMENT
                                    value)
                                .second();
                        }
                    }
                    """.replace("CALL_COMMENT", comments[0]).replace("ARG_COMMENT", comments[1]);

            String singlePass = new Formatter(config, true).format(input);
            String commentLine = singlePass.lines().filter(line -> line.contains(comments[1])).findFirst().orElseThrow();
            String argumentLine = singlePass.lines().filter(line -> line.stripLeading().startsWith("value)")).findFirst().orElseThrow();
            assertThat(argumentLine.substring(0, argumentLine.indexOf("value)")))
                    .isEqualTo(commentLine.substring(0, commentLine.indexOf(comments[1])));

            String output = formatter.format(input);

            assertThat(output).containsOnlyOnce(comments[0]).containsOnlyOnce(comments[1]);
            assertThat(output.indexOf(comments[0])).isLessThan(output.indexOf(".first("));
            assertThat(output.indexOf(comments[1])).isGreaterThan(output.indexOf(".first("));
            assertThatCode(() -> StaticJavaParser.parse(output)).doesNotThrowAnyException();
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void emptyCommentsBetweenManyArguments_arePreservedAndConverge(WrapStyle style) {
        String input = """
                class T {
                    void m() {
                        receiver.call(//
                                Alpha.class,//
                                Bravo.class,//
                                Charlie.class,//
                                Delta.class,//
                                Echo.class,//
                                Foxtrot.class,//
                                Golf.class,//
                                Hotel.class,//
                                India.class,//
                                Juliet.class,//
                                Kilo.class,//
                                Lima.class,//
                                Mike.class,//
                                November.class//
                        );
                    }
                }
                """;
        Formatter formatter = new Formatter(FormatterConfig.builder().lineLength(120).wrapStyle(style).build());

        String output = formatter.format(input);

        assertThat(StaticJavaParser.parse(output).getAllContainedComments()).hasSize(15);
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void commentsBeforeLaterShortArguments_stayInsideCall(WrapStyle style) {
        Formatter formatter = new Formatter(FormatterConfig.builder().lineLength(120).wrapStyle(style).build());
        for (String comment : new String[] {"//", "// second argument"}) {
            String input = "class T {void m(){receiver.call(1,\n" + comment + "\n2);}}";

            String output = formatter.format(input);

            assertThat(StaticJavaParser.parse(output).getAllContainedComments()).hasSize(1);
            assertThat(output.indexOf(comment)).isGreaterThan(output.indexOf("call("));
            assertThat(output.indexOf(comment)).isLessThan(output.indexOf("2"));
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    private static final Formatter DEFAULT = new Formatter(FormatterConfig.defaults());
    private static final Formatter JAVA_25 =
            new Formatter(
                    FormatterConfig.builder()
                            .indentSize(2)
                            .lineLength(100)
                            .javaLanguageLevel(JavaLanguageLevel.of(25))
                            .build());

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

    @Test
    void spotlessFenceAroundPrimitiveArray_preservesClosingMarkerBeforeBrace() {
        String input =
                """
                class T {
                  static final byte[] RANGES = {
                    // spotless:off
                    0, 0, 0, 1,
                    (byte) 0xFF, (byte) 0xFF,
                    // spotless:on
                  };
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out).containsPattern("// spotless:on\\R\\s*}");
    }

    @Test
    void spotlessFenceInsideAnnotationArray_preservesClosingMarkerBeforeBrace() {
        String input =
                """
                import org.junit.jupiter.params.provider.CsvSource;

                class T {
                  @CsvSource(
                      value = {
                        // spotless:off
                        "appId | token | remote-config-id",
                        // spotless:on
                      },
                      delimiterString = "|")
                  void test() {}
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out.indexOf("// spotless:on")).isLessThan(out.indexOf("},"));
        assertThat(out.indexOf("},")).isLessThan(out.indexOf("delimiterString"));
    }

    @Test
    void spotlessFenceInsideMethodArguments_preservesClosingMarkerBeforeParen() {
        String input =
                """
                class T {
                  Object arguments(Object... values) { return values; }

                  java.util.stream.Stream<Object> cases() {
                    return java.util.stream.Stream.of(
                        arguments(
                            // spotless:off
                            "alpha",
                            "bravo"
                            // spotless:on
                            ));
                  }
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out).containsPattern("// spotless:on\\R\\s*\\)\\)");
        assertThat(out).doesNotContainPattern("// spotless:on\\R\\s*\\R\\s*\\)");
    }

    @Test
    void spotlessFenceAfterTrailingExpressionLambda_preservesClosingMarkerOnce() {
        String input =
                """
                class T {
                  void consume(String value, java.util.function.IntUnaryOperator operator) {}

                  void test() {
                    // spotless:off
                    consume("a deliberately long argument that forces the trailing lambda call to wrap", x -> x + 1 // spotless:on
                    );
                  }
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out)
                .contains(
                        "a deliberately long argument that forces the trailing lambda call to wrap\", x -> x\n");
        assertClosingMarkerBeforeParen(out);
    }

    @Test
    void spotlessFenceAfterShortTrailingExpressionLambda_preservesClosingMarkerOnce() {
        String input =
                """
                class T {
                  void consume(String value, java.util.function.IntUnaryOperator operator) {}

                  void test() {
                    // spotless:off
                    consume("short", x -> x + 1 // spotless:on
                    );
                  }
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out).contains("consume(\"short\", x -> x + 1\n");
        assertClosingMarkerBeforeParen(out);
    }

    @Test
    void spotlessFenceInsideHoistedMethodChainArguments_preservesClosingMarker() {
        String input =
                """
                class T {
                  Chain source() { return null; }

                  Object test() {
                    // spotless:off
                    return source()
                        .transform("a deliberately long first argument", //
                            "a deliberately long second argument"
                            // spotless:on
                            )
                        .finish();
                  }

                  interface Chain {
                    Chain transform(String first, String second);
                    Object finish();
                  }
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertClosingMarkerBeforeParen(out);
        assertThat(out.lines().filter(line -> line.strip().equals("//")).count()).isOne();
    }

    @Test
    void spotlessFenceAfterTrailingLambdaInHoistedMethodChain_preservesClosingMarker() {
        String input =
                """
                class T {
                  Chain source() { return null; }

                  Object test() {
                    // spotless:off
                    return source().transform("a deliberately long first argument that forces the method chain to wrap", //
                        x -> x + 1 // spotless:on
                        ).finish();
                  }

                  interface Chain {
                    Chain transform(String s, java.util.function.IntUnaryOperator f);
                    Object finish();
                  }
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertClosingMarkerBeforeParen(out);
        assertThat(out.lines().filter(line -> line.strip().equals("//")).count()).isOne();
    }

    @Test
    void spotlessFenceInsideEmptyArray_preservesMarkersBeforeBrace() {
        String input =
                """
                class T {
                  int[] values = {
                    // spotless:off
                    // spotless:on
                  };
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out)
                .contains(
                        """
                          int[] values = {
                              // spotless:off
                              // spotless:on
                          };
                        """);
    }

    @Test
    void spotlessFenceInsideNestedEmptyArray_usesNestedElementIndent() {
        String input =
                """
                class T {
                  int[][] values = {
                    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19},
                    {
                      // spotless:off
                      // spotless:on
                    }
                  };
                }
                """;

        String out = formatAndAssertFenceFixedPoint(input);

        assertThat(out)
                .contains(
                        """
                              {
                                  // spotless:off
                                  // spotless:on
                              }
                        """);
    }

    @Test
    void emptyArrayWithoutComments_remainsInline() {
        String input = "class T { int[] values = {}; }";

        String out = JAVA_25.format(input);

        assertThat(out).contains("int[] values = {};");
        StaticJavaParser.parse(out);
        assertThat(JAVA_25.format(out)).isEqualTo(out);
    }

    private static String formatAndAssertFenceFixedPoint(String input) {
        String out = JAVA_25.format(input);
        assertThat(out).containsOnlyOnce("// spotless:off").containsOnlyOnce("// spotless:on");
        assertThat(out.indexOf("// spotless:off")).isLessThan(out.indexOf("// spotless:on"));
        StaticJavaParser.parse(out);
        assertThat(JAVA_25.format(out)).isEqualTo(out);
        return out;
    }

    private static void assertClosingMarkerBeforeParen(String output) {
        assertThat(output).containsPattern("// spotless:on\\R\\s*\\)");
    }
}
