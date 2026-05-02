package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 5: comments survive formatting and formatting is idempotent when comments are present. */
class CommentPreservationTest {

    private static final Formatter DEFAULT = new Formatter(FormatterConfig.defaults());

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
}
