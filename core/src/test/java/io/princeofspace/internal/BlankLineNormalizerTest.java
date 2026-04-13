package io.princeofspace.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlankLineNormalizerTest {

    @Test
    void blankAfterOpenBrace_removed() {
        assertThat(BlankLineNormalizer.normalize("{\n\n    foo();\n}"))
                .isEqualTo("{\n    foo();\n}");
    }

    @Test
    void blankBeforeCloseBrace_removed() {
        assertThat(BlankLineNormalizer.normalize("{\n    foo();\n\n}"))
                .isEqualTo("{\n    foo();\n}");
    }

    @Test
    void consecutiveBlanks_collapsedToOne() {
        assertThat(BlankLineNormalizer.normalize("a\n\n\nb"))
                .isEqualTo("a\n\nb");
    }

    @Test
    void threeBlanks_collapsedToOne() {
        assertThat(BlankLineNormalizer.normalize("a\n\n\n\nb"))
                .isEqualTo("a\n\nb");
    }

    @Test
    void singleBlankBetweenStatements_preserved() {
        assertThat(BlankLineNormalizer.normalize("a\n\nb"))
                .isEqualTo("a\n\nb");
    }

    @Test
    void blankAtStartOfBlock_andEndOfBlock_bothRemoved() {
        String input = "void m() {\n\n    a();\n    b();\n\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("void m() {\n    a();\n    b();\n}");
    }

    @Test
    void blankBetweenMethodsInBlock_preserved() {
        String input = "class T {\n    void a() {}\n\n    void b() {}\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("class T {\n    void a() {}\n\n    void b() {}\n}");
    }

    @Test
    void blankAfterTypeDeclarationOpenBrace_preserved() {
        String input = "class T {\n\n    void a() {}\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("class T {\n\n    void a() {}\n}");
    }

    @Test
    void blankAfterNestedTypeDeclarationOpenBrace_removed() {
        String input = "class Outer {\n    interface Inner {\n\n        void run();\n    }\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("class Outer {\n    interface Inner {\n        void run();\n    }\n}");
    }

    @Test
    void sourceWithNoBlankLines_unchanged() {
        String input = "class T {\n    void m() {\n        return;\n    }\n}";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo(input);
    }

    @Test
    void emptyString_unchanged() {
        assertThat(BlankLineNormalizer.normalize("")).isEqualTo("");
    }

    @Test
    void blankBeforeElse_removed() {
        // "}" starts the else line — treated as close-brace context
        String input = "if (x) {\n    a();\n\n} else {\n    b();\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("if (x) {\n    a();\n} else {\n    b();\n}");
    }

    @Test
    void blankAfterOpenBrace_andBeforeCloseBrace_bothRemoved() {
        String input = "{\n\n    x();\n\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("{\n    x();\n}");
    }
}
