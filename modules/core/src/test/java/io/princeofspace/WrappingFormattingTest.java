package io.princeofspace;


import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 wrapping: regression tests for line-breaking behavior (method chains, binary/ternary,
 * type clauses). Full {@code examples/} golden parity is a larger follow-up once the pretty-printer
 * matches every edge case in {@code FormatterShowcase}.
 */
class WrappingFormattingTest {

    @Test
    void throwsClause_balanced_wrapsEachExceptionType() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(55)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m() throws java.io.IOException, java.sql.SQLException, java.lang.IllegalStateException {
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("throws");
        assertThat(out).contains("java.io.IOException");
        assertThat(out).contains("\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    private static void assertNoLineLongerThan(String formatted, int maxLineLength) {
        String[] lines = formatted.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            assertThat(lines[i].length())
                    .as("line %d must not exceed maxLineLength=%d: %s", i, maxLineLength, lines[i])
                    .isLessThanOrEqualTo(maxLineLength);
        }
    }

    private static String lineContaining(String text, String needle) {
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.contains(needle)) {
                    return line;
                }
                lineStart = i + 1;
            }
        }
        throw new AssertionError("Missing line containing: " + needle);
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static String nextNonEmptyLineAfter(String text, String needle) {
        boolean foundNeedle = false;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (foundNeedle && !line.isBlank()) {
                    return line;
                }
                if (line.contains(needle)) {
                    foundNeedle = true;
                }
                lineStart = i + 1;
            }
        }
        throw new AssertionError("Missing non-empty line after: " + needle);
    }

    @Test
    void methodChain_wrapsEachSegmentWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(22)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        return a.b().c().d();
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains(".c()");
        assertThat(out).contains(".d()");
        assertThat(out).contains("\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void lambdaHeavyChain_wrapsEvenWhenUnderPreferredLineLength() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                import java.util.List;

                class T {
                    String m(List<String> items, String query) {
                        return items.stream().filter(item -> item.contains(query)).findFirst().orElse(null);
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        return items\n");
        assertThat(out).contains("            .stream()\n");
        assertThat(out).contains("            .filter(item -> item.contains(query))\n");
        assertThat(out).contains("            .findFirst()\n");
        assertThat(out).contains("            .orElse(null);\n");
    }

    @Test
    void lambdaHeavyChain_withNestedLambda_wrapsBySegment() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.concurrent.ExecutorService;

                class T {
                    void m(List<String> urls, ExecutorService executor) {
                        var futures = urls.stream().map(url -> executor.submit(() -> fetch(url))).toList();
                    }

                    String fetch(String url) { return url; }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        var futures = urls\n");
        assertThat(out).contains("            .stream()\n");
        assertThat(out).contains("            .map(url -> executor.submit(() -> fetch(url)))\n");
        assertThat(out).contains("            .toList();\n");
    }

    @Test
    void lambdaHeavyChain_insideLogicalExpression_staysInline() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                import java.util.List;

                class T {
                    boolean m(String legacyField, List<String> items, java.util.Map<String, String> complexGenericField) {
                        return legacyField != null && !legacyField.isBlank() && items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isBlank()) && complexGenericField != null && complexGenericField.size() > 0;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("            && items.stream().allMatch(item -> item != null && !item.isBlank())\n");
    }

    @Test
    void logicalAnd_wrapsWithOperatorAtContinuationStart() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .build());
        String input =
                """
                class T {
                    boolean m(boolean a, boolean b, boolean c) {
                        return a && b && c;
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("&&");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void implementsClause_balanced_wrapsEachType() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                "public class T implements java.io.Serializable, java.lang.Cloneable, AutoCloseable {}";
        String out = f.format(input);
        assertThat(out).contains("implements");
        assertThat(out).contains("java.io.Serializable");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void extendsClause_balanced_wrapsEachInterface() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(70)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                "interface T extends java.io.Serializable, java.lang.Cloneable, java.util.function.Supplier<String>, java.util.function.Consumer<String> {}";

        String out = f.format(input);

        assertThat(out).contains("interface T\n");
        assertThat(out).contains("extends java.io.Serializable,\n");
        assertThat(out).contains("java.lang.Cloneable,\n");
        assertThat(out).contains("java.util.function.Supplier<String>,\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void implementsClause_narrow_cont8_doubleIndentsWrappedTypes() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());
        String input =
                "public class T implements java.io.Serializable, java.lang.Cloneable, AutoCloseable {}";

        String out = f.format(input);

        assertThat(out)
                .contains(
                        """
                        public class T
                                implements
                                        java.io.Serializable,
                                        java.lang.Cloneable,
                                        AutoCloseable
                        {""");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void permitsClause_narrow_wrapsEvenWhenInlineWouldFit() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.NARROW)
                                .closingParenOnNewLine(false)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {
                    record Circle(double radius) implements Shape {}
                    record Rectangle(double width, double height) implements Shape {}
                    record Triangle(double base, double height) implements Shape {}
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        """
                        sealed interface Shape
                                permits Shape.Circle,
                                        Shape.Rectangle,
                                        Shape.Triangle {""");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void implementsClause_wide_usesDeclarationColumnWhenGreedyPacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                "public class FormatterShowcase implements Comparable<FormatterShowcase>, java.io.Serializable, Cloneable, AutoCloseable {}";

        String out = f.format(input);

        assertThat(out)
                .contains(
                        """
                        public class FormatterShowcase
                                implements Comparable<FormatterShowcase>, java.io.Serializable, Cloneable, AutoCloseable
                        {""");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void implementsClause_wide_keepsLastTypeOnWrappedLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                "public class FormatterShowcase implements Comparable<FormatterShowcase>, java.io.Serializable, Cloneable, AutoCloseable {}";

        String out = f.format(input);

        assertThat(out)
                .contains(
                        """
                        public class FormatterShowcase
                                implements Comparable<FormatterShowcase>, java.io.Serializable, Cloneable, AutoCloseable
                        {""");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void constructorParameters_wide_useOpeningColumnWhenGreedyPacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class FormatterShowcase {
                    public FormatterShowcase(String legacyField, List<String> items, Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction, String defaultLocale, ExecutorService executorService) {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("public FormatterShowcase(String legacyField, List<String> items,\n");
        assertThat(out)
                .contains(
                        "        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction,\n");
        assertThat(out).contains("        String defaultLocale, ExecutorService executorService\n");
        assertThat(out).contains("    ) {\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void constructorParameters_wide_cont8_keepLastParameterOnWrappedLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.ExecutorService;

                class FormatterShowcase {
                    public FormatterShowcase(String legacyField, List<String> items, Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction, String defaultLocale, ExecutorService executorService) {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("    public FormatterShowcase(String legacyField, List<String> items,\n");
        assertThat(out)
                .contains(
                        "            Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction,\n");
        assertThat(out)
                .contains(
                        "            String defaultLocale, ExecutorService executorService) {\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void lambdaParameterList_insideWrappedChainCall_alignsParametersAndCloseParen() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(38)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        // Force a wrapped chain and a two-parameter lambda whose formal parameter list also wraps.
        String input =
                """
                class T {
                    T f() { return this; }
                    T g(java.util.function.BiFunction<Object, Object, Object> o) { return this; }

                    void m() {
                        f()
                                .g((
                        com.example.VeryLongParameterTypeNameOne a,
                        com.example.VeryLongParameterTypeNameTwo b) -> a);
                    }
                }
                """;
        String out = f.format(input);
        String closeLine = lineContaining(out, ") -> a");
        int openParen = leadingSpaces(closeLine);
        String p1 = lineContaining(out, "com.example.VeryLongParameterTypeNameOne a,");
        String p2 = lineContaining(out, "com.example.VeryLongParameterTypeNameTwo b");
        Assertions.assertEquals(
                openParen + 8,
                leadingSpaces(p1),
                () -> "openParen column from `) ->`, full out:\n" + out);
        Assertions.assertEquals(leadingSpaces(p1), leadingSpaces(p2), out);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void blockLambdaInFirstWrappedChainCall_indentsRelativeToChainContinuation() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.ExecutorService;

                class T {
                    ExecutorService executorService;

                    void m() {
                        CompletableFuture.supplyAsync(() -> { loadData(); return processData(); }, executorService).thenApply(result -> transformResult(result)).thenAccept(finalResult -> consume(finalResult));
                    }

                    void loadData() {}
                    String processData() { return ""; }
                    String transformResult(String result) { return result; }
                    void consume(String result) {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        CompletableFuture\n");
        assertThat(out).contains(".supplyAsync(() -> {\n");
        assertThat(out).contains("                loadData();\n");
        assertThat(out).contains("                return processData();\n");
        assertThat(out).contains("            }, executorService)\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void blockLambdaInFirstWrappedChainCall_honorsContinuationIndentSize() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.ExecutorService;

                class T {
                    ExecutorService executorService;

                    void m() {
                        CompletableFuture.supplyAsync(() -> { loadData(); return processData(); }, executorService).thenApply(result -> transformResult(result)).thenAccept(finalResult -> consume(finalResult));
                    }

                    void loadData() {}
                    String processData() { return ""; }
                    String transformResult(String result) { return result; }
                    void consume(String result) {}
                }
                """;

        String out = f.format(input);

        // After Rule 7's chain-continuation carve-out (TDR-015), wrapped chains use indentSize for
        // continuation, so a block lambda body inside the first chain segment lands at
        // lineStart + continuationIndentSize (= 8 + 8 = 16) rather than chain+continuation.
        assertThat(out).contains("                loadData();\n");
        assertThat(out).contains("                return processData();\n");
        assertThat(out).contains("            }, executorService)\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void nestedWrappedCall_placesEachClosingParenOnOwnLineWhenEnabled() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(90)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.Map;
                import java.util.stream.Collectors;

                class T {
                    Map<String, List<String>> m(List<String> items) {
                        return items.stream().collect(Collectors.groupingBy(item -> key(item), Collectors.mapping(String::toLowerCase, Collectors.toList())));
                    }

                    String key(String item) { return item; }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains(".collect(Collectors.groupingBy(item -> key(item),\n");
        assertThat(out)
                .contains("                Collectors.mapping(String::toLowerCase, Collectors.toList())\n");
        assertThat(out).containsPattern("\\n\\s*\\)\\n\\s*\\);");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void nestedLambdaCall_doesNotEmitDanglingClosingParenBeforeSemicolon() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(56)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class T {
                    void cappedLogNoCustomerData(java.util.function.Consumer<Logger> consumer) {}

                    void m(Logger logger) {
                        cappedLogNoCustomerData(l -> l.warn("Bad thing happened and we have lots of information to tell you", new IllegalStateException("Bad thing happened and this stack is also very long")));
                    }

                    interface Logger {
                        void warn(String message, Throwable throwable);
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).doesNotContain("\n            )\n            ;");
        assertThat(out).doesNotContain("\n            )\n        );");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void nestedBlockLambdaWarnCall_placesClosingParensOnOwnLinesWhenEnabled() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class T {
                    void cappedLogNoCustomerData(java.util.function.Consumer<Logger> consumer) {}

                    void m() {
                        cappedLogNoCustomerData(
                                l -> {
                                    l.warn("Bad thing happened and we have lots of information to tell you", new IllegalArgumentException("Bad thing happened and this is very long"));
                                });
                    }

                    interface Logger {
                        void warn(String message, Throwable throwable);
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).containsPattern("\\n\\s*\\)\\n\\s*\\);");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void methodParameters_wide_packByPhysicalWidthAfterFirstForcedWrap() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.function.Function;

                class T {
                    public <A extends Comparable<A> & java.io.Serializable, B extends List<? super A>> B transformAndCollect(List<A> source, Function<A, B> transformer, java.util.function.BinaryOperator<B> combiner) {
                        return null;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains("    public <A extends Comparable<A> & java.io.Serializable, B extends List<? super A>> B transformAndCollect(\n");
        assertThat(out)
                .contains(
                        "        List<A> source, Function<A, B> transformer, java.util.function.BinaryOperator<B> combiner\n");
        assertThat(out).contains("    ) {\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void methodParameters_wide_cont8_keepThirdParameterOnSameWrappedLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.function.Function;

                class T {
                    public <A extends Comparable<A> & java.io.Serializable, B extends List<? super A>> B transformAndCollect(List<A> source, Function<A, B> transformer, java.util.function.BinaryOperator<B> combiner) {
                        return null;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "            List<A> source, Function<A, B> transformer, java.util.function.BinaryOperator<B> combiner) {\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeParameters_balanced_wrapsEachWhenOverflow() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(90)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    <A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, C extends java.util.Map<String, java.util.List<java.util.Optional<String>>>> void m() {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("<\n");
        assertThat(out).contains("A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(out).contains("B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeParameters_wide_packsGreedily() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    <A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, C extends java.util.Map<String, java.util.List<java.util.Optional<String>>>> void m() {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("<A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(out).contains("B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeParameters_narrow_oneParameterPerLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());
        String input =
                """
                class T {
                    <A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>, C extends java.util.Map<String, java.util.List<java.util.Optional<String>>>> void m() {}
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("<\n");
        assertThat(out).contains("A extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(out).contains("B extends java.util.Map<String, java.util.List<java.util.Optional<String>>>,\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void arrayInitializer_wide_keepsRoomForClosingBrace() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    static final String[] DEFAULT_COLUMNS = {"id", "name", "email", "created_at", "updated_at", "status", "role", "department"};
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("    static final String[] DEFAULT_COLUMNS =\n");
        assertThat(out).contains("        {\"id\", \"name\", \"email\", \"created_at\", \"updated_at\", \"status\", \"role\", \"department\"};\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void arrayInitializer_balanced_putsEachElementOnItsOwnLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(70)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    static final String[] DEFAULT_COLUMNS = {"id", "name", "email", "created_at", "updated_at", "status", "role", "department"};
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("{\n");
        assertThat(out).contains("\"id\",\n");
        assertThat(out).contains("\"name\",\n");
        assertThat(out).contains("\"email\",\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void arrayInitializer_narrow_putsEachElementOnItsOwnLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(70)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());
        String input =
                """
                class T {
                    static final String[] DEFAULT_COLUMNS = {"id", "name", "email", "created_at", "updated_at", "status", "role", "department"};
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("{\n");
        assertThat(out).contains("\"id\",\n");
        assertThat(out).contains("\"name\",\n");
        assertThat(out).contains("\"email\",\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void enumConstants_wide_accountForDeclarationColumnWhenGreedyPacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    enum HttpStatus {
                        OK(200, "OK"), CREATED(201, "Created"), BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), INTERNAL_SERVER_ERROR(500, "Internal Server Error");
                        private final int code;
                        private final String message;
                        HttpStatus(int code, String message) { this.code = code; this.message = message; }
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "        OK(200, \"OK\"), CREATED(201, \"Created\"), BAD_REQUEST(400, \"Bad Request\"), UNAUTHORIZED(401, \"Unauthorized\"),\n");
        assertThat(out)
                .contains(
                        "        FORBIDDEN(403, \"Forbidden\"), NOT_FOUND(404, \"Not Found\"), INTERNAL_SERVER_ERROR(500, \"Internal Server Error\");\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void enumConstants_wide_cont8_keepThreeConstantsOnSecondLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    enum HttpStatus {
                        OK(200, "OK"), CREATED(201, "Created"), BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), INTERNAL_SERVER_ERROR(500, "Internal Server Error");
                        private final int code;
                        private final String message;
                        HttpStatus(int code, String message) { this.code = code; this.message = message; }
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "        OK(200, \"OK\"), CREATED(201, \"Created\"), BAD_REQUEST(400, \"Bad Request\"), UNAUTHORIZED(401, \"Unauthorized\"),\n");
        assertThat(out)
                .contains(
                        "        FORBIDDEN(403, \"Forbidden\"), NOT_FOUND(404, \"Not Found\"), INTERNAL_SERVER_ERROR(500, \"Internal Server Error\");\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void balancedSingleWrappedLambdaArgument_staysInlineAfterOpeningParen() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                import java.util.List;

                class T {
                    void m(List<String> items) {
                        items.stream().filter(item -> { String trimmed = item.trim(); return !trimmed.isEmpty() && trimmed.length() > 3; }).forEach(System.out::println);
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("            .filter(item -> {\n");
        assertThat(out).contains("                String trimmed = item.trim();\n");
        assertThat(out).contains("            })\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void balancedSingleWrappedMethodCallArgument_staysInlineAfterOpeningParen() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(90)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                import java.util.List;
                import java.util.Map;
                import java.util.stream.Collectors;

                class T {
                    Map<String, List<String>> m(List<String> items) {
                        return items.stream().collect(Collectors.groupingBy(item -> key(item), Collectors.mapping(String::toLowerCase, Collectors.toList())));
                    }

                    String key(String item) { return item; }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("            .collect(Collectors.groupingBy(\n");
        assertThat(out).contains("                item -> key(item),\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_balanced_putsEachOperandOnItsOwnLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .javaLanguageLevel(JavaLanguageLevel.of(8))
                                .build());
        String input =
                """
                class T {
                    String m(String legacyField, java.util.List<String> items) {
                        String traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. " + "Please review them at your earliest convenience. " + "If you have any questions, please contact support.";
                        return traditional;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        String traditional = \"Hello \"\n");
        assertThat(out).contains("            + legacyField\n");
        assertThat(out).contains("            + \", you have \"\n");
        assertThat(out).contains("            + items.size()\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_balanced_java21_putsEachOperandOnItsOwnLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                class T {
                    String buildMessage(String legacyField, java.util.List<String> items) {
                        var traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. " + "Please review them at your earliest convenience. " + "If you have any questions, please contact support.";
                        return traditional;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        var traditional = \"Hello \"\n");
        assertThat(out).contains("            + legacyField\n");
        assertThat(out).contains("            + \", you have \"\n");
        assertThat(out).contains("            + items.size()\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_wide_keepsGreedyPacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                class T {
                    String buildMessage(String legacyField, java.util.List<String> items) {
                        var traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. " + "Please review them at your earliest convenience. " + "If you have any questions, please contact support.";
                        return traditional;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "        var traditional = \"Hello \" + legacyField + \", you have \" + items.size() + \" items in your collection. \"\n");
        assertThat(out)
                .contains(
                        "                + \"Please review them at your earliest convenience. \"\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_narrow_staysOneOperandPerLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());
        String input =
                """
                class T {
                    String m(String legacyField, java.util.List<String> items) {
                        String traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. " + "Please review them at your earliest convenience. " + "If you have any questions, please contact support.";
                        return traditional;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        String traditional = \"Hello \"\n");
        assertThat(out).contains("            + legacyField\n");
        assertThat(out).contains("            + \", you have \"\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void logicalAnd_balanced_staysOneOperandPerLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    boolean m(String legacyField, java.util.List<String> items, java.util.Map<String, String> complexGenericField) {
                        return legacyField != null && !legacyField.isEmpty() && items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isEmpty()) && complexGenericField != null && complexGenericField.size() > 0;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        return legacyField != null\n");
        assertThat(out).contains("            && !legacyField.isEmpty()\n");
        assertThat(out).contains("            && items != null\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void logicalAnd_narrow_putsEachOperandOnItsOwnLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());
        String input =
                """
                class T {
                    boolean m(String legacyField, java.util.List<String> items, java.util.Map<String, String> complexGenericField) {
                        return legacyField != null && !legacyField.isEmpty() && items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isEmpty()) && complexGenericField != null && complexGenericField.size() > 0;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out).contains("        return legacyField != null\n");
        assertThat(out).contains("            && !legacyField.isEmpty()\n");
        assertThat(out).contains("            && items != null\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void logicalAnd_wide_cont8_keepsPairPackingOnFirstTwoLines() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    boolean m(String legacyField, java.util.List<String> items, java.util.Map<String, String> complexGenericField) {
                        return legacyField != null && !legacyField.isEmpty() && items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isEmpty()) && complexGenericField != null && complexGenericField.size() > 0;
                    }
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "        return legacyField != null && !legacyField.isEmpty() && items != null && !items.isEmpty()\n");
        assertThat(out)
                .contains(
                        "                && items.stream().allMatch(item -> item != null && !item.isEmpty()) && complexGenericField != null\n");
        assertThat(out).contains("                && complexGenericField.size() > 0;\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void logicalAnd_withWrappedMethodChainOperand_chainDotsAtContinuationIndent() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(70)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                import java.util.List;

                class T {
                    boolean m(List<String> items, String q) {
                        return items != null && items.stream().map(String::trim).filter(s -> !s.isEmpty()).anyMatch(s -> s.contains(q));
                    }
                }
                """;
        String out = f.format(input);
        String operatorLine = lineContaining(out, "&& items");
        String firstChainLine = lineContaining(out, ".stream()");
        assertThat(leadingSpaces(firstChainLine))
                .isEqualTo(leadingSpaces(operatorLine) + 4);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForWideMethodParameters() {
        int max = 55;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                class TightMargins {
                    void m(
                            String aaaaaaaaaaa,
                            String bbbbbbbbbbb,
                            String ccccccccccc,
                            String ddddddddddd,
                            String eeeeeeeeeee) {}
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForWideBinaryAndChain() {
        int max = 58;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(52)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class TightMargins {
                    boolean m(boolean x0, boolean x1, boolean x2, boolean x3, boolean x4) {
                        return x0 && x1 && x2 && x3 && x4;
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForWideStringConcatenation() {
        int max = 56;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(48)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class TightMargins {
                    String m() {
                        return "aaaaaaaaaaa"
                                + "bbbbbbbbbbb"
                                + "ccccccccccc"
                                + "ddddddddddd"
                                + "eeeeeeeeeee";
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForWideImplementsClause() {
        int max = 62;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(55)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                "public class TightMargins implements AA.AAAAAAAAAAA, BB.BBBBBBBBBBB, CC.CCCCCCCCCCC, DD.DDDDDDDDDDD, EE.EEEEEEEEEEE {}";
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForWideArrayInitializer() {
        int max = 54;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(46)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class TightMargins {
                    String[] NAMES = {
                        "aaaaaaaaaaa",
                        "bbbbbbbbbbb",
                        "ccccccccccc",
                        "ddddddddddd",
                        "eeeeeeeeeee"
                    };
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForMultiCatchUnionTypes() {
        int max = 60;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        try {
                        } catch (java.io.IOException | java.lang.IllegalStateException | java.lang.IllegalArgumentException | java.lang.RuntimeException e) {
                        }
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForAssertMessageString() {
        int max = 55;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String longMsg = "x".repeat(120);
        String input = "class T { void m(boolean a) { assert a : \"" + longMsg + "\"; } }";
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void assertMessageString_chunking_prefersWordBoundaries() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(110)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m(boolean enabled, String legacyField, java.util.List<String> items) {
                        assert enabled : "legacyField and items must be populated before calling longAssert in production systems with strict validation rules enabled";
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("strict validation");
        assertThat(out).doesNotContain("systems with s\"\n            + \"trict");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForLongGenericTypeUse() {
        int max = 70;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    java.util.function.BiFunction<java.util.Map.Entry<String, java.util.List<String>>, java.util.Map.Entry<String, java.util.List<String>>, Integer> cmp = (left, right) -> 0;
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void fieldInitializer_longStringLiteral_wrapsAfterEqualsWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class BootstrapUtils {
                    private static final String DEFAULT_CACHE_AWARE_CONTEXT_LOADER_DELEGATE_CLASS_NAME = "org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate";
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, 120);
        assertThat(out).contains("DEFAULT_CACHE_AWARE_CONTEXT_LOADER_DELEGATE_CLASS_NAME =\n");
        assertThat(out).contains("\"org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate\"");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void fieldInitializer_stringConcatLiteralsOnly_wrapsAfterEqualsWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String half = "x".repeat(52);
        String input =
                "class T {\n    private static final String P = \"" + half + "\" + \"" + half + "\";\n}\n";
        String out = f.format(input);
        assertNoLineLongerThan(out, 120);
        assertThat(out).contains("P =\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void fieldInitializer_textBlock_wrapsAfterEqualsWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                "class T {\n"
                        + "    private static final String DOC = \"\"\"\n"
                        + "        "
                        + "y".repeat(95)
                        + "\n"
                        + "        \"\"\";\n"
                        + "}\n";
        String out = f.format(input);
        assertNoLineLongerThan(out, 120);
        assertThat(out).contains("DOC =\n");
        assertThat(out).contains("\"\"\"");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void fieldInitializer_breaksAfterEquals_whenInlineWouldOverflow_andKeepsRhsSingleLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(100)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    static final java.util.function.Function<java.util.Map<String, Integer>, Integer> LOOKUP = map -> map.getOrDefault("ab", 0);
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("LOOKUP =\n");
        assertThat(out).contains("        map -> map.getOrDefault(\"ab\", 0);");
        assertThat(out).doesNotContain("map ->\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void wrappedArguments_useContinuationIndent_notMethodNameColumnAlignment() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        saveWithVeryLongMethodNameForAlignmentRegression("alphaAlphaAlpha", "betaBetaBeta", "gammaGammaGamma", "deltaDeltaDelta");
                    }

                    void saveWithVeryLongMethodNameForAlignmentRegression(String a, String b, String c, String d) {}
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("saveWithVeryLongMethodNameForAlignmentRegression(\n");
        String callLine = lineContaining(out, "saveWithVeryLongMethodNameForAlignmentRegression(");
        String firstArgLine = nextNonEmptyLineAfter(out, "saveWithVeryLongMethodNameForAlignmentRegression(");
        assertThat(leadingSpaces(firstArgLine)).isEqualTo(leadingSpaces(callLine) + 8);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void wrappedArguments_closingParenStaysAtCallIndent_whenClosingParenOnNewLineEnabled() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(60)
                                .wrapStyle(WrapStyle.WIDE)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        saveWithVeryLongMethodNameForAlignmentRegression("alphaAlphaAlpha", "betaBetaBeta", "gammaGammaGamma", "deltaDeltaDelta");
                    }

                    void saveWithVeryLongMethodNameForAlignmentRegression(String a, String b, String c, String d) {}
                }
                """;
        String out = f.format(input);
        String callLine = lineContaining(out, "saveWithVeryLongMethodNameForAlignmentRegression(");
        String closingLine = lineContaining(out, ");");
        assertThat(leadingSpaces(closingLine)).isEqualTo(leadingSpaces(callLine));
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void closingParen_singleBlockLambdaArg_onItsOwnLineWhenEnabled() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(45)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        runLaterWithVeryLongNameToForceWrapping(() -> { doWork(); });
                    }

                    void runLaterWithVeryLongNameToForceWrapping(Runnable action) {}
                }
                """;
        String out = f.format(input);
        assertThat(out)
                .containsPattern(
                        "runLaterWithVeryLongNameToForceWrapping\\(\\(\\) -> \\{\\n\\s*doWork\\(\\);\\n\\s*}\\n\\s*\\);");
        assertThat(out).contains("\n        );\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void closingParen_singleNestedCall_onItsOwnLineWhenEnabled() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(40)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(true)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        consume(buildVeryLongName("alpha", "beta"));
                    }

                    void consume(String value) {}

                    String buildVeryLongName(String a, String b) {
                        return a + b;
                    }
                }
                """;
        String out = f.format(input);
        String callLine = lineContaining(out, "consume(");
        String closingLine = lineContaining(out, ");");
        assertThat(closingLine.stripLeading()).isEqualTo(");");
        assertThat(leadingSpaces(closingLine)).isEqualTo(leadingSpaces(callLine));
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void lambdaBody_bitwiseChains_wrapWithoutDeepAlignment() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m(java.util.List actualMethods, java.util.Set forcePublic) {
                        java.util.List methods = CollectionUtils.transform(actualMethods, value -> {
                            Method method = (Method) value;
                            int modifiers = Constants.ACC_FINAL
                                | (method.getModifiers()
                                & ~Constants.ACC_ABSTRACT
                                & ~Constants.ACC_NATIVE
                                & ~Constants.ACC_SYNCHRONIZED);
                            if (forcePublic.contains(MethodWrapper.create(method))) {
                                modifiers = (modifiers & ~Constants.ACC_PROTECTED) | Constants.ACC_PUBLIC;
                            }
                            return ReflectUtils.getMethodInfo(method, modifiers);
                        });
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, 150);
        assertThat(out).contains("        int modifiers = Constants.ACC_FINAL\n");
        assertThat(out).contains("            | (method.getModifiers()\n");
        assertThat(out).contains("                & ~Constants.ACC_ABSTRACT\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void nonChainSingleScopedCall_blockLambdaArgument_usesContinuationIndentNotDeepAlign() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(70)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m(java.util.List actualMethods, java.util.Set forcePublic) {
                        java.util.List methods = CollectionUtils.transform(actualMethods, value -> {
                            Method method = (Method) value;
                            int modifiers = Constants.ACC_FINAL | (method.getModifiers() & ~Constants.ACC_ABSTRACT & ~Constants.ACC_NATIVE & ~Constants.ACC_SYNCHRONIZED);
                            return ReflectUtils.getMethodInfo(method, modifiers);
                        });
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, 120);
        assertThat(out).contains("CollectionUtils.transform(actualMethods, value -> {\n");
        String lambdaBodyLine = lineContaining(out, "Method method = (Method) value;");
        assertThat(leadingSpaces(lambdaBodyLine)).isGreaterThan(0);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void longFieldLambdaInitializers_breakAfterEquals_likeSpringConfigurationClassParser() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                import java.util.Comparator;
                import java.util.function.Predicate;

                class T {
                    private static final Predicate<Condition> REGISTER_BEAN_CONDITION_FILTER = condition -> (condition instanceof ConfigurationCondition configurationCondition && ConfigurationPhase.REGISTER_BEAN.equals(configurationCondition.getConfigurationPhase()));
                    private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR = (o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, 150);
        assertThat(out).contains("REGISTER_BEAN_CONDITION_FILTER =\n");
        assertThat(out).contains("DEFERRED_IMPORT_COMPARATOR =\n");
        assertThat(out).contains("        condition ->");
        assertThat(out).contains("        (o1, o2) ->");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForLongStringLiteralInMethodArgument() {
        int max = 120;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        throw new IllegalStateException("Component scan for configuration class [%s] could not be used with conditions in REGISTER_BEAN phase: %s".formatted("a", "b"));
                    }
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(out).contains("throw new IllegalStateException(\"");
        assertThat(out).contains("+ \"");
        assertThat(out).contains(".formatted(\"a\", \"b\")");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void emptyStringLiteral_doesNotCrashWhenChunkPathIsSelected() {
        int max = 60;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    String reallyLongPrefixNameForLineLengthPressure = "";
                }
                """;
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void veryLongStringLiteral_idempotent_whenChunkedThenBinaryExpr() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String longText = "z".repeat(30_000);
        String input = "class T {\n    private static final String S = \"" + longText + "\";\n}\n";
        String once = f.format(input);
        assertThat(f.format(once)).as("idempotent after second pass parses concat / parens").isEqualTo(once);
    }

    @Test
    void textBlockFormattedCall_wrapsArgumentsWhenLineWouldExceedMax() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());
        String input =
                """
                class T {
                    void m(String a, String b, String c) {
                        throw new IllegalStateException(\"\"\"
                                Package-private method [%s] declared in class [%s] cannot be advised by \
                                CGLIB-proxied handler class [%s], because it is effectively private.
                                \"\"\".formatted(a.toLowerCase(), b.stripTrailing(), c.replace("-", "_")));
                    }
                }
                """;
        String out = f.format(input);
        // Chain segments after a wrapped text-block receiver use the chain-continuation indent
        // (block + indentSize = 8 + 4 = 12 with default config); see Rule 7 / TDR-015.
        assertThat(out).contains("\n            .formatted(");
        assertThat(out).contains("a.toLowerCase()");
        assertThat(out).contains("b.stripTrailing()");
        assertThat(out).contains("c.replace(\"-\", \"_\")");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void forLoopHeader_balanced_wrapsAllThreeClauses() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(46)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                class T {
                    void m(int n) {
                        for (int i = 0, j = 0; i < n && j < n + 1 && n > 0; i++, j++) {
                            System.out.println(i);
                        }
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("for (");
        assertThat(out).contains("i++, j++");
        assertThat(out).contains("int i = 0, j = 0");
        int semiAfterInit = out.indexOf("int i = 0, j = 0");
        int semi1 = out.indexOf(';', semiAfterInit);
        int nlAfterFirst = out.indexOf('\n', semi1);
        int semi2 = out.indexOf(';', semi1 + 1);
        assertThat(semi2).isGreaterThan(nlAfterFirst);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void forEachLoopHeader_balanced_wrapsAfterColon() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                class T {
                    void m(java.util.List<String> xs) {
                        for (String s : xs.getItems().getInner().getValues().asList().asIterable()) {
                        }
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("for (String s :");
        assertThat(out)
                .as("iterable should continue on a new line after the colon when the header is too long")
                .contains(":\n");
        assertThat(out).contains("asIterable()");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void tryWithResources_balanced_indentsByContinuationIndentNotKeyword() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(52)
                                .wrapStyle(WrapStyle.BALANCED)
                                .closingParenOnNewLine(false)
                                .build());
        String input =
                """
                class T {
                    void m() {
                        try (java.io.InputStream a = null;
                            java.io.InputStream b = null;
                            java.io.InputStream c = null) {
                        }
                    }
                }
                """;
        String out = f.format(input);
        String tryLine = lineContaining(out, "try (");
        String secondRes = lineContaining(out, "java.io.InputStream b");
        assertThat(leadingSpaces(secondRes)).isEqualTo(leadingSpaces(tryLine) + 8);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void switchCaseLabel_balanced_wrapsEachLabelWhenOverflow() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(38)
                                .wrapStyle(WrapStyle.BALANCED)
                                .javaLanguageLevel(JavaLanguageLevel.of(17))
                                .build());
        String input =
                """
                class T {
                    int m(int x) {
                        switch (x) {
                            case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10:
                                return 0;
                            default:
                                return 1;
                        }
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("case ");
        assertThat(out).contains("1,");
        assertThat(out).contains("10:");
        assertThat(out)
                .as("BALANCED breaks the case label list across lines when the header overflows")
                .containsPattern("case\\s+\\R\\s+1,");
        assertThat(f.format(out)).isEqualTo(out);
    }
}
