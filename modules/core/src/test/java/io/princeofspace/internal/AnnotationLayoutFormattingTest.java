package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end annotation layout is enforced by the pretty printer (after {@link BraceEnforcer} on the AST).
 * These tests document required shapes from {@code docs/formatting-rules.md} and guard regressions.
 */
class AnnotationLayoutFormattingTest {

    @Test
    void declarationAnnotations_eachOnOwnLine() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out =
                f.format(
                        "@Deprecated @SuppressWarnings(\"all\") public class T { public T() {} }");
        assertThat(out).contains("@Deprecated");
        assertThat(out).contains("@SuppressWarnings(\"all\")");
        assertThat(out.indexOf("@Deprecated"))
                .isLessThan(out.indexOf("@SuppressWarnings"));
        assertThat(out).containsPattern("@Deprecated\\R");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeUseAnnotations_stayInline() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out =
                f.format("import java.util.List; class T { void m(List<@org.eclipse.jdt.annotation.NonNull String> p) {} }");
        assertThat(out).contains("List<@org.eclipse.jdt.annotation.NonNull String>");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void mixed_declAndTypeUse_keepRespectiveStyles() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String in =
                """
                class T {
                    @Deprecated
                    @SuppressWarnings("all")
                    java.lang.@org.eclipse.jdt.annotation.NonNull String name = "x";
                }
                """;
        String out = f.format(in);
        assertThat(out).contains("java.lang.@org.eclipse.jdt.annotation.NonNull String");
        assertThat(out).contains("@Deprecated");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void longAnnotation_wrapsPairsBeforeSplittingStrings() {
        Formatter f = new Formatter(FormatterConfig.builder().build());
        String in =
                """
                @Generated(value = "com.amazing.super.awesome.CodeGenerator", date = "2026-10-21T02:26:43Z", comments = "I've always wanted to be cool")
                class T {}
                """;
        String out = f.format(in);
        assertThat(out)
                .startsWith(
                        """
                        @Generated(
                                value = "com.amazing.super.awesome.CodeGenerator",
                                date = "2026-10-21T02:26:43Z",
                                comments = "I've always wanted to be cool"
                        )
                        """);
        assertThat(out).doesNotContain("\" +");
        assertThat(f.format(out)).isEqualTo(out);
    }

    private static final int NARROW_LINE_LENGTH = 60;
    private static final int LINE_LENGTH = 120;
    private static final int BOUNDARY_FILL = 102;
    private static final String CLOSE_DECL = ") class T {}";
    private static final String WRAPPED_END = "\n)\nclass T";
    private static final String SUPPRESS_OPEN = "@SuppressWarnings(";
    private static final String LONG_TOKEN =
            "\"this particular suppression token is intentionally long to overflow the line limit when inline now, ok\"";

    @Test
    void singleMemberAnnotation_wrapsBeforeSplittingString() {
        Formatter f = new Formatter(FormatterConfig.builder().build());
        String out = f.format(SUPPRESS_OPEN + LONG_TOKEN + CLOSE_DECL);
        assertThat(out).startsWith("@SuppressWarnings(\n        " + LONG_TOKEN + "\n)\nclass T");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void singlePairAnnotation_wrapsBeforeSplittingString() {
        Formatter f = new Formatter(FormatterConfig.builder().build());
        String out = f.format(SUPPRESS_OPEN + "value = " + LONG_TOKEN + CLOSE_DECL);
        assertThat(out).startsWith("@SuppressWarnings(\n        value = " + LONG_TOKEN + "\n)\nclass T");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void singleMemberAnnotation_staysWithinLineLengthAtIndentedBoundary() {
        // Column 0 after println() would let this literal fill the whole line; the continuation indent
        // is applied only when it prints, so the value line would be lineLength + 2.
        for (FormatterConfig cfg : annotationBoundaryConfigs()) {
            int fill = cfg.lineLength() - cfg.continuationIndentSize() - (cfg.closingParenOnNewLine() ? 0 : 1);
            assertLinesFit(cfg, SUPPRESS_OPEN + "\"" + "x".repeat(fill) + "\"" + CLOSE_DECL);
        }
    }

    @Test
    void nonFinalAnnotationPair_staysWithinLineLengthBeforeTrailingComma() {
        // "first = " is 8 columns, matching the existing last-value boundary. The literal fills the
        // continuation line exactly, then the separator comma would make it lineLength + 1.
        for (FormatterConfig cfg : annotationBoundaryConfigs()) {
            int fill = cfg.lineLength() - cfg.continuationIndentSize() - "first = ".length() - 2;
            assertLinesFit(
                    cfg, "@LongAnnotationName(first = \"" + "x".repeat(fill) + "\", last = \"tail\") class T {}");
        }
    }

    @Test
    void inlineClosingParen_reservesColumnBeforeSplittingLastValue() {
        Formatter f = new Formatter(FormatterConfig.builder().closingParenOnNewLine(false).build());
        // 8 (continuation) + "value = " (8) + 104 quoted columns = 120: fits alone, but not with the trailing ')'.
        String boundary = "\"" + "x".repeat(BOUNDARY_FILL) + "\"";
        for (String in :
                new String[] {
                    SUPPRESS_OPEN + "value = " + boundary + CLOSE_DECL,
                    SUPPRESS_OPEN + "first = \"a\", value = " + boundary + CLOSE_DECL
                }) {
            String out = f.format(in);
            assertThat(out.lines().mapToInt(String::length).max().orElse(0)).isLessThanOrEqualTo(LINE_LENGTH);
            assertThat(f.format(out)).isEqualTo(out);
        }
    }

    @Test
    void singlePairArrayAnnotation_keepsHuggingParen() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(NARROW_LINE_LENGTH).build());
        String out =
                f.format(
                        "@Shelf(descriptors = {\"alpha-alpha-alpha\", \"bravo-bravo-bravo\", \"charlie-charlie\"}) class T {}");
        assertThat(out).startsWith("@Shelf(descriptors = {\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void parameterAnnotation_wrapsRelativeToParameterList() {
        Formatter f = new Formatter(FormatterConfig.builder().build());
        String in =
                "class T { void m(@Foo(first = \"aaaaaaaaaa bbbbbbbbbb cccccccccc dddddd\","
                        + " second = \"eeeeeeeeee ffffffffff gggggggggg hhhhhh\","
                        + " third = \"iiiiiiiiii jjjjjjjjjj kkkkkkkkkk llllll\") int p) {} }";
        String out = f.format(in);
        assertThat(out).contains("    void m(\n            @Foo(\n                    first = ");
        assertThat(out).contains("\n            ) int p\n    )");
        assertThat(f.format(out)).isEqualTo(out);
    }

    /**
     * Lines with several distinct strings must break at structural points (arguments, operators, ternary
     * branches, chain segments, array elements) before any string literal is split into chunks.
     */
    @Test
    void multiStringLines_neverSplitStringsWhenStructureCanWrap() {
        Formatter f = new Formatter(FormatterConfig.builder().build());
        String a = "\"aaaaaaaaaa bbbbbbbbbb cccccccccc dddddd\"";
        String b = "\"eeeeeeeeee ffffffffff gggggggggg hhhhhh\"";
        String c = "\"iiiiiiiiii jjjjjjjjjj kkkkkkkkkk llllll\"";
        String in =
                "class T { void m(String s) {"
                        + " log.info(" + a + ", " + b + ", " + c + ");"
                        + " String x = s.equals(" + a + ") ? " + b + " : " + c + " + s;"
                        + " String y = " + a + " + s + " + b + " + s + " + c + ";"
                        + " String[] z = {" + a + ", " + b + ", " + c + ", \"q\"};"
                        + " var w = list.stream().map(t -> t + " + a + ").filter(t -> t.contains(" + b + "))"
                        + ".count(" + c + ");"
                        + " } }";
        String out = f.format(in);
        for (String literal : new String[] {a, b, c}) {
            assertThat(out).contains(literal);
        }
        assertThat(f.format(out)).isEqualTo(out);
    }

    private static FormatterConfig[] annotationBoundaryConfigs() {
        FormatterConfig[] configs = new FormatterConfig[WrapStyle.values().length * 4];
        int i = 0;
        for (WrapStyle style : WrapStyle.values()) {
            for (boolean closingOnNewLine : new boolean[] {true, false}) {
                for (int lineLength : new int[] {NARROW_LINE_LENGTH, LINE_LENGTH}) {
                    configs[i++] =
                            FormatterConfig.builder()
                                    .wrapStyle(style)
                                    .closingParenOnNewLine(closingOnNewLine)
                                    .lineLength(lineLength)
                                    .build();
                }
            }
        }
        return configs;
    }

    private static void assertLinesFit(FormatterConfig cfg, String source) {
        Formatter f = new Formatter(cfg);
        String out = f.format(source);
        int longest = out.lines().mapToInt(String::length).max().orElse(0);
        assertThat(longest)
                .as(
                        "style=%s closingParenOnNewLine=%s lineLength=%s%n%s",
                        cfg.wrapStyle(),
                        cfg.closingParenOnNewLine(),
                        cfg.lineLength(),
                        out)
                .isLessThanOrEqualTo(cfg.lineLength());
        assertThat(f.format(out)).isEqualTo(out);
    }
}
