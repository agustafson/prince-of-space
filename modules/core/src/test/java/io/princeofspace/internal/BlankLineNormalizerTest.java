package io.princeofspace.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
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
    void class_noBlankLineAfterOpeningBrace() {
        String input = "class T {\n\n    void a() {}\n}";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo("class T {\n    void a() {}\n}");
    }

    @Test
    void interface_noBlankLineAfterOpeningBrace() {
        String input = "interface I {\n\n    void run();\n}";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo("interface I {\n    void run();\n}");
    }

    @Test
    void enum_noBlankLineAfterOpeningBrace() {
        String input = "enum E {\n\n    A\n}";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo("enum E {\n    A\n}");
    }

    @Test
    void nestedInterface_noBlankLineAfterOpeningBrace() {
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

    @Test
    void blankLineBetweenImports_preserved() {
        String input =
                """
                import java.util.List;

                import java.util.Map;

                class T {}
                """;
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo(input);
    }

    @Test
    void blankLineBetweenImports_preservedWhenWhitespaceAfterImportKeywordIsTab() {
        String input = "import\tjava.util.List;\n\nimport\tjava.util.Map;\n\nclass T {}\n";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo(input);
    }

    @Test
    void textBlockBlanks_preservedWhileStructuralBlanksAreRemoved() {
        String literal = "\"\"\"\n{\n\n}\na\n\n\nb\n\"\"\"";
        String input = "class T {\n\nString s = " + literal + ";\n\n\nvoid m() {\n\n}\n\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("class T {\nString s = " + literal + ";\n\nvoid m() {\n}\n}");
    }

    @Test
    void tokenOffsets_accountForCommentsTabsCrLfGenericsAndShifts() {
        String literal = "\"\"\"\r\n\t{\r\n\r\n\t}\r\n\r\n\r\n\t\"\"\"";
        String prefix = "\t/* \"\"\"\r\n comment */\r\nclass T {\r\n";
        String declarations = "java.util.List<java.util.List<java.util.List<java.util.List<String>>>> a;\r\n"
                + "int b = 32 >>> 2; { b >>= 1; b >>>= 1; }\f\r\n";
        String values = "String s = " + literal + " + " + literal + ";\r\n";
        String suffix = "} // trailing EOF";
        String input = prefix + "\r\n" + declarations + "\r\n\r\n" + values + "\r\n\r\n" + suffix;
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo(prefix + declarations + "\r\n" + values + suffix);
    }

    @Test
    void quoteSequencesInCommentsAndStrings_doNotProtectStructuralBlanks() {
        String input = "class T {\n/* \"\"\" */\n\n\nString s = \"\\\"\\\"\\\"\";\n\n\n}";
        assertThat(BlankLineNormalizer.normalize(input))
                .isEqualTo("class T {\n/* \"\"\" */\n\nString s = \"\\\"\\\"\\\"\";\n}");
    }

    @Test
    void modernSyntaxAndEscapedDelimiters_keepLiteralBoundaries() {
        String input = "/// \"\"\" in markdown\nrecord R(String s) {\n"
                + "String f(Object o) { return switch (o) {\n"
                + "case String x when !x.isEmpty() -> \"\"\"\n\\\"\"\"\n\n\nend\n\"\"\";\n"
                + "default -> \"\\u0000\";\n}; }\n}\n";
        assertThat(BlankLineNormalizer.normalize(input)).isEqualTo(input);
    }
}
