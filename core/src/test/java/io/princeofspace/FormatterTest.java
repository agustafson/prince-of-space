package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatterTest {

    private static final Formatter DEFAULT = new Formatter(FormatterConfig.defaults());

    // ── braces ────────────────────────────────────────────────────────────────

    @Test
    void bracelessIf_endToEnd_bracesAdded() {
        String input = """
                class T {
                    String m(int x) {
                        if (x < 0) return "neg";
                        else return "pos";
                    }
                }
                """;
        String output = DEFAULT.format(input);
        assertThat(output)
                .contains("if (x < 0) {")
                .contains("return \"neg\";")
                .contains("} else {")
                .contains("return \"pos\";");
    }

    @Test
    void bracelessFor_endToEnd_bracesAdded() {
        String input = """
                class T {
                    void m() {
                        for (int i = 0; i < 10; i++) x();
                    }
                }
                """;
        String output = DEFAULT.format(input);
        assertThat(output).matches("(?s).*for \\(.*\\) \\{.*x\\(\\);.*\\}.*");
    }

    @Test
    void bracelessWhile_endToEnd_bracesAdded() {
        String input = """
                class T {
                    void m() {
                        while (running) tick();
                    }
                }
                """;
        assertThat(DEFAULT.format(input)).contains("while (running) {").contains("tick();");
    }

    // ── annotations ───────────────────────────────────────────────────────────

    @Test
    void multipleAnnotationsOnSameLine_eachOnOwnLine() {
        String input = """
                class T {
                    @Override @Deprecated
                    void m() {}
                }
                """;
        String output = DEFAULT.format(input);
        // @Override and @Deprecated must each appear on their own line
        assertThat(output).containsPattern("@Override\\s*\\n\\s*@Deprecated");
    }

    @Test
    void fieldAnnotation_onOwnLine() {
        String input = """
                class T {
                    @Deprecated private String s;
                }
                """;
        String output = DEFAULT.format(input);
        assertThat(output).containsPattern("@Deprecated\\s*\\n\\s*private String s;");
    }

    // ── blank lines ───────────────────────────────────────────────────────────

    @Test
    void extraBlankLinesInMethod_collapsed() {
        String input = """
                class T {
                    void m() {
                        a();


                        b();
                    }
                }
                """;
        String output = DEFAULT.format(input);
        // Should not contain two consecutive blank lines
        assertThat(output).doesNotContain("\n\n\n");
    }

    @Test
    void blankLineAfterOpenBrace_removed() {
        String input = """
                class T {
                    void m() {

                        a();
                    }
                }
                """;
        String output = DEFAULT.format(input);
        // The method body should start immediately after "{"
        assertThat(output).doesNotMatch("(?s).*\\{\\s*\\n\\s*\\n.*");
    }

    // ── indentation ───────────────────────────────────────────────────────────

    @Test
    void defaultConfig_indentsWithFourSpaces() {
        String input = "class T { void m() { int x = 1; } }";
        String output = DEFAULT.format(input);
        // method body indented with 4 spaces
        assertThat(output).contains("    void m()");
        assertThat(output).contains("        int x = 1;");
    }

    @Test
    void twoSpaceIndent_respected() {
        Formatter f = new Formatter(FormatterConfig.builder().indentSize(2).build());
        String output = f.format("class T { void m() { int x = 1; } }");
        assertThat(output).contains("  void m()");
        assertThat(output).contains("    int x = 1;");
    }

    @Test
    void tabIndent_respected() {
        Formatter f = new Formatter(FormatterConfig.builder()
                .indentStyle(IndentStyle.TABS)
                .indentSize(1)
                .build());
        String output = f.format("class T { void m() { int x = 1; } }");
        assertThat(output).contains("\tvoid m()");
        assertThat(output).contains("\t\tint x = 1;");
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void invalidJava_throwsFormatterException() {
        assertThatThrownBy(() -> DEFAULT.format("this is not java {{ {"))
                .isInstanceOf(FormatterException.class)
                .hasMessageContaining("Parse failed");
    }

    @Test
    void formatWithPath_parseError_includesPathInMessage() {
        java.nio.file.Path path = java.nio.file.Path.of("src/Foo.java");
        assertThatThrownBy(() -> DEFAULT.format("not java", path))
                .isInstanceOf(FormatterException.class)
                .hasMessageContaining("src/Foo.java");
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void idempotency_simpleClass() {
        String input = """
                class T {
                    void m() {
                        if (x) doA();
                        else doB();
                    }
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void idempotency_annotations() {
        String input = """
                class T {
                    @Override @Deprecated
                    void m() {}
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void idempotency_multipleBlankLines() {
        String input = """
                class T {
                    void a() {}


                    void b() {}
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }
}
