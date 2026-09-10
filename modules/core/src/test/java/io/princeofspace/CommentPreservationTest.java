package io.princeofspace;

import com.github.javaparser.StaticJavaParser;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 5: comments survive formatting and formatting is idempotent when comments are present. */
class CommentPreservationTest {

    private static final Formatter DEFAULT = new Formatter(FormatterConfig.defaults());
    private static final Formatter JAVA_25 =
            new Formatter(
                    FormatterConfig.builder()
                            .indentSize(2)
                            .lineLength(100)
                            .javaLanguageLevel(JavaLanguageLevel.of(25))
                            .build());

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
    void blockCommentBetweenArrayInitializerElements_forcesMultilineAndPreservesComment() {
        String input =
                """
                class T {
                    static final String[] X = {"alpha", /* sep */ "beta", "gamma"};
                }
                """;
        String out = DEFAULT.format(input);
        // Inline {a, /* c */ b} cannot survive: default block-comment visitor adds a trailing
        // newline. Forcing multi-line keeps the comment as a normalized leading comment on "beta".
        assertThat(out).contains("sep");
        assertThat(out).contains("\"alpha\",\n");
        assertThat(out).contains("\"beta\",\n");
        assertThat(out).contains("\"gamma\"\n");
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
