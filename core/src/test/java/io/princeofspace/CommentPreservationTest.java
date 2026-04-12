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
}
