package io.princeofspace;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
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
                                .preferredLineLength(55)
                                .maxLineLength(100)
                                .continuationIndentSize(4)
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

    @Test
    void methodChain_wrapsEachSegmentWhenPreferredExceeded() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(22)
                                .maxLineLength(80)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .javaLanguageLevel(LanguageLevel.JAVA_21)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .javaLanguageLevel(LanguageLevel.JAVA_21)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .javaLanguageLevel(LanguageLevel.JAVA_21)
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
                                .preferredLineLength(50)
                                .maxLineLength(100)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(50)
                                .maxLineLength(120)
                                .continuationIndentSize(4)
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
    void implementsClause_narrow_cont8_doubleIndentsWrappedTypes() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(50)
                                .maxLineLength(120)
                                .continuationIndentSize(8)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.NARROW)
                                .closingParenOnNewLine(false)
                                .javaLanguageLevel(LanguageLevel.JAVA_21)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
    void implementsClause_wide_cont8_keepsLastTypeOnWrappedLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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
    void blockLambdaInFirstWrappedChainCall_indentsRelativeToChainContinuation() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(60)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(60)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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

        assertThat(out).contains("                    loadData();\n");
        assertThat(out).contains("                    return processData();\n");
        assertThat(out).contains("                }, executorService)\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void nestedWrappedCall_gluesStackedClosingParens() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(90)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
        assertThat(out).contains("            ));\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void methodParameters_wide_packByPhysicalWidthAfterFirstForcedWrap() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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
    void arrayInitializer_wide_keepsRoomForClosingBrace() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class T {
                    static final String[] DEFAULT_COLUMNS = {"id", "name", "email", "created_at", "updated_at", "status", "role", "department"};
                }
                """;

        String out = f.format(input);

        assertThat(out)
                .contains(
                        "    static final String[] DEFAULT_COLUMNS = {\"id\", \"name\", \"email\", \"created_at\", \"updated_at\", \"status\",\n");
        assertThat(out).contains("        \"role\", \"department\"};\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void enumConstants_wide_accountForDeclarationColumnWhenGreedyPacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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
                                .preferredLineLength(60)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(90)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
    void stringConcatenation_balanced_keepsGreedyFirstLinePacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .javaLanguageLevel(LanguageLevel.JAVA_8)
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

        assertThat(out)
                .contains(
                        "        String traditional = \"Hello \" + legacyField + \", you have \" + items.size() + \" items in your collection. \"\n");
        assertThat(out)
                .contains(
                        "            + \"Please review them at your earliest convenience. \" + \"If you have any questions, please contact support.\";\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_balanced_java21_keepsGreedyFirstLinePacking() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.BALANCED)
                                .javaLanguageLevel(LanguageLevel.JAVA_21)
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
                        "            + \"Please review them at your earliest convenience. \" + \"If you have any questions, please contact support.\";\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void stringConcatenation_narrow_staysOneOperandPerLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(8)
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
    void maxLineLength_enforcedForWideMethodParameters() {
        int max = 55;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(50)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(52)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(48)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(55)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(46)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(50)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(40)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String longMsg = "x".repeat(120);
        String input = "class T { void m(boolean a) { assert a : \"" + longMsg + "\"; } }";
        String out = f.format(input);
        assertNoLineLongerThan(out, max);
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void maxLineLength_enforcedForLongGenericTypeUse() {
        int max = 70;
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(60)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
    void lambdaBody_bitwiseChains_wrapWithoutDeepAlignment() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(70)
                                .maxLineLength(120)
                                .continuationIndentSize(4)
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
        assertThat(out).contains("CollectionUtils.transform(\n");
        assertThat(out).contains("            actualMethods,\n");
        assertThat(out).contains("            value -> {\n");
        assertThat(out).contains("                Method method = (Method) value;\n");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void longFieldLambdaInitializers_breakAfterEquals_likeSpringConfigurationClassParser() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .preferredLineLength(120)
                                .maxLineLength(150)
                                .continuationIndentSize(4)
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
                                .preferredLineLength(120)
                                .maxLineLength(max)
                                .continuationIndentSize(4)
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
}
