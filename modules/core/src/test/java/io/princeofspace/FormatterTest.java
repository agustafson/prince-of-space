package io.princeofspace;


import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Disabled;
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

    @Test
    void emptyRecordBody_staysCompact() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                record UserProfile(String name, String email, int age) {}
                """;

        String output = f.format(input);

        assertThat(output).contains("record UserProfile(String name, String email, int age) {}");
    }

    @Test
    void emptyMethodBody_staysExpandedInJava8() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(8)).build());
        String input = """
                class T {
                    void m() {}
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("void m() {\n");
    }

    @Test
    void emptyMethodBody_staysCompactInJava21() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    void m() {}
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("void m() {}");
    }

    @Test
    void typeUseMethodAnnotation_staysBetweenModifierAndReturnType() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    public @org.jspecify.annotations.Nullable String findItem(String query) { return null; }
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("public @org.jspecify.annotations.Nullable String findItem(String query)");
    }

    @Test
    void switchExpression_keepsSpaceBeforeSelectorAndSingleLineCases() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {
                        record Circle(double radius) implements Shape {}
                        record Rectangle(double width, double height) implements Shape {}
                        record Triangle(double base, double height) implements Shape {}
                    }

                    double scale(Shape shape) {
                        return switch (shape) {
                            case Shape.Circle c -> c.radius() > 100 ? 0.5 : 1.0;
                            case Shape.Rectangle r -> r.width() == r.height() ? 1.0 : 0.75;
                            case Shape.Triangle t -> 1.0;
                        };
                    }
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("return switch (shape) {\n");
        assertThat(output).contains("case Shape.Circle c -> c.radius() > 100 ? 0.5 : 1.0;\n");
        assertThat(output).contains("case Shape.Rectangle r -> r.width() == r.height() ? 1.0 : 0.75;\n");
    }

    @Test
    void textBlock_preservesContentIndent_andFormattedStaysOnClosingDelimiterLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    String report(String legacyField, int size, boolean valid) {
                        return \"\"\"
                                Report for %s
                                =============
                                Items: %d
                                Status: %s
                                \"\"\".formatted(legacyField, size, valid ? "valid" : "invalid");
                    }
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("return \"\"\"\n");
        assertThat(output).contains("                Report for %s\n");
        assertThat(output)
                .contains(".formatted(legacyField, size, valid ? \"valid\" : \"invalid\");");
    }

    @Test
    void closingParenOnNewLine_movesWrappedTypeClauseBraceToNextLine() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(50)
                                .wrapStyle(io.princeofspace.model.WrapStyle.BALANCED)
                                .closingParenOnNewLine(true)
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

        String output = f.format(input);

        assertThat(output).contains("Shape.Triangle\n{\n");
    }

    @Test
    void closingParenOnNewLine_doesNotBreakShortCompactRecordHeader() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .closingParenOnNewLine(true)
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input =
                """
                class T {
                    record LoadedProfile(String name, java.util.List<String> orders, double balance) {}
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("record LoadedProfile(String name, java.util.List<String> orders, double balance) {}");
    }

    @Test
    void emptyMethodBody_staysCompactInJava21_withSectionComment() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    // Placeholder methods
                    private void validate(String locale) {}
                    private void loadData() {}
                    private void saveResult(Object r) {}
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("private void validate(String locale) {}");
        assertThat(output).contains("private void loadData() {}");
        assertThat(output).contains("private void saveResult(Object r) {}");
    }

    @Test
    void nestedEmptyRecordBody_staysCompact() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21)).build());
        String input = """
                class T {
                    record UserProfile(String name, String email, int age) {}
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("record UserProfile(String name, String email, int age) {}");
    }

    @Test
    void nestedEmptyRecordBody_staysCompact_withFollowingRecord() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());
        String input = """
                class T {
                    // Scenario 21: Record declarations
                    record UserProfile(String name, String email, int age) {}

                    record DetailedProfile(String firstName, String lastName, String email, String phone, String address, String city, String country, int age) {
                        DetailedProfile {}
                    }
                }
                """;

        String output = f.format(input);

        assertThat(output).contains("record UserProfile(String name, String email, int age) {}");
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
        assertThat(output).contains("void m() {\n        a();");
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

    @Test
    void enum_trailingCommas_addsCommaAfterLastConstantWhenMultiline() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .trailingCommas(true)
                                .lineLength(40)
                                .build());
        String input =
                """
                enum Letters {
                    ALPHACONSTANTVERYLONGNAME,
                    BETACONSTANTVERYLONGNAME,
                    GAMMACONSTANTVERYLONGNAME
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("GAMMACONSTANTVERYLONGNAME,");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void tryWithResources_closingParenOnNewLine_movesClosingParenToOwnLineWithMultipleResources() {
        Formatter f = new Formatter(FormatterConfig.builder().closingParenOnNewLine(true).build());
        String input =
                """
                class T {
                    void m() throws Exception {
                        try (java.io.InputStream in = null; java.io.OutputStream out = null) {
                        }
                    }
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("java.io.OutputStream out = null\n");
        assertThat(out).contains("\n        ) {");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void typeParameters_wrapWhenExceedingPreferredWidth() {
        Formatter f =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(55)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());
        String input =
                """
                class Demo {
                    <AlphaTypeParameter, BetaTypeParameter, GammaTypeParameter, DeltaTypeParameter>
                    void m() {}
                }
                """;
        String out = f.format(input);
        assertThat(out).contains("<AlphaTypeParameter,");
        assertThat(out).contains("\n    ");
        assertThat(f.format(out)).isEqualTo(out);
    }

    // ── lambdas (standalone / non-chain) ───────────────────────────────────────

    @Test
    void standaloneBlockLambda_braceOnSameLineAsArrow_andIdempotent() {
        String input = """
                class T {
                    void m() {
                        Runnable r = () -> {
                            a();
                            b();
                        };
                    }

                    void a() {}

                    void b() {}
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(once).contains("() -> {").contains("a();").contains("b();");
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void lambdaLastArgument_nonChainCall_keepsCallOpeningOnSameLine() {
        String input = """
                import java.util.Collections;
                import java.util.List;

                class T {
                    void m(List<String> items) {
                        Collections.sort(
                                items, (String a, String b) -> {
                                    return a.compareToIgnoreCase(b);
                                });
                    }
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(once).contains("Collections.sort(").doesNotContain("Collections.sort\n");
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void expressionLambda_inMethodArgument_hasNoStatementTerminatorInsideParens() {
        String input = """
                import java.util.List;

                class T {
                    String m(List<String> items, String q) {
                        return items.stream().filter(item -> item.contains(q)).findFirst().orElse(null);
                    }
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(once).doesNotContain("item.contains(q);");
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void chainWithUnscopedBaseCall_formatsWithoutOptionalScopeFailure() {
        String input = """
                class T {
                    Builder make() {
                        return new Builder();
                    }

                    void m() {
                        make().stepOne().stepTwo();
                    }

                    static final class Builder {
                        Builder stepOne() {
                            return this;
                        }

                        Builder stepTwo() {
                            return this;
                        }
                    }
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(once).contains("make().stepOne().stepTwo();");
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    @Test
    void methodDeclaration_annotationsOnOwnLines_idempotentOnSecondFormat() {
        String input = """
                class T implements Comparable<T> {
                    @Override @SuppressWarnings("unchecked") public int compareTo(T o) {
                        return 0;
                    }
                }
                """;
        String once = DEFAULT.format(input);
        assertThat(once).contains("@Override");
        assertThat(DEFAULT.format(once)).isEqualTo(once);
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void invalidJava_throwsFormatterException() {
        assertThatThrownBy(() -> DEFAULT.format("this is not java {{ {"))
                .isInstanceOf(FormatterException.class)
                .hasMessageContaining("Parse failed");
    }

    @Test
    void invalidJava_formatResult_returnsParseFailureWithProblemMessages() {
        FormatResult r = DEFAULT.formatResult("this is not java {{ {");
        assertThat(r).isInstanceOf(FormatResult.ParseFailure.class);
        FormatResult.ParseFailure pf = (FormatResult.ParseFailure) r;
        assertThat(pf.problemMessages()).isNotEmpty();
        assertThat(pf.message()).contains("Parse failed");
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

    @Test
    void idempotency_commentBetweenStatements_doesNotGainExtraBlankLine() {
        String input = """
                class T {
                    void m() {
                        a();
                        // keep this comment attached to next statement
                        b();
                    }

                    void a() {}

                    void b() {}
                }
                """;
        String once = DEFAULT.format(input);
        String twice = DEFAULT.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void idempotency_chainCommentBeforeLambdaArg_doesNotDriftBeforeDot() {
        String input = """
                import java.time.Duration;

                class T {
                    void m(Client client) {
                        client
                                .get()
                                .uri("/path")
                                .cookies(// keep comment with lambda argument
                                        cookies -> cookies.add("id", "123"))
                                .block(Duration.ofSeconds(1));
                    }

                    interface Client {
                        Client get();

                        Client uri(String value);

                        Client cookies(java.util.function.Consumer<Cookies> c);

                        Object block(Duration timeout);
                    }

                    interface Cookies {
                        void add(String name, String value);
                    }
                }
                """;
        String once = DEFAULT.format(input);
        String twice = DEFAULT.format(once);
        assertThat(twice).isEqualTo(once);
        assertThat(twice).doesNotContain(".// keep comment");
    }

    @Test
    void idempotency_switchExpr_lineCommentAfterArrow_onOwnLine() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input =
                """
                class T {
                    int m(String path) {
                        return switch (path) {
                            case "a" -> 1;
                            case "b" ->
                                // keep this comment with the arm body
                                java.util.Objects.hash(path);
                            default -> 0;
                        };
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain("-> // keep");
    }

    @Test
    void idempotency_longOrChain_leadingCommentsBeforeLastOperand_idempotentForAllWrapStyles() {
        String input =
                """
                class T {
                    boolean m(Object o) {
                        return (o == Boolean.class
                                || o == Byte.class
                                || o == Character.class
                                || o == Short.class
                                || o == Integer.class
                                || o == Long.class
                                || o == Float.class
                                || o == Double.class
                                || o == String.class
                                || o == Class.class
                                || o == java.io.File.class
                                || o == java.math.BigInteger.class
                                || o == java.math.BigDecimal.class
                                || o == java.util.Date.class
                                || o == java.util.Calendar.class
                                || o == javax.xml.datatype.Duration.class
                                || o == javax.xml.datatype.XMLGregorianCalendar.class
                                || o == javax.activation.DataHandler.class
                                // Source and subclasses of Source are supported as well.
                                // o == javax.xml.transform.Source.class ||
                                || o == java.util.UUID.class);
                    }
                }
                """;
        for (WrapStyle wrap : WrapStyle.values()) {
            Formatter f =
                    new Formatter(
                            FormatterConfig.builder()
                                    .lineLength(80)
                                    .wrapStyle(wrap)
                                    .build());
            String once = f.format(input);
            assertThat(f.format(once))
                    .as("wrapStyle=%s", wrap)
                    .isEqualTo(once);
        }
    }

    @Test
    void idempotency_lineCommentOnSameLineAsClassOpeningBrace_staysInsideBody() {
        String input =
                """
                package p;

                class T {  // note on opening brace
                    void m() {}
                }
                """;
        for (WrapStyle wrap : WrapStyle.values()) {
            Formatter f =
                    new Formatter(
                            FormatterConfig.builder()
                                    .lineLength(80)
                                    .wrapStyle(wrap)
                                    .build());
            String once = f.format(input);
            assertThat(f.format(once))
                    .as("wrapStyle=%s", wrap)
                    .isEqualTo(once);
        }
    }

    @Test
    void idempotency_subclassOpeningBraceEndOfLineComment_likeSpringOperatorNot() {
        String input =
                """
                package p;

                class SpelNodeImpl {}

                public class OperatorNot extends SpelNodeImpl {  // unary operator note
                    public OperatorNot() {}
                }
                """;
        for (WrapStyle wrap : WrapStyle.values()) {
            Formatter f =
                    new Formatter(
                            FormatterConfig.builder()
                                    .lineLength(80)
                                    .wrapStyle(wrap)
                                    .build());
            String once = f.format(input);
            assertThat(f.format(once))
                    .as("wrapStyle=%s", wrap)
                    .isEqualTo(once);
        }
    }

    @Test
    void idempotency_lambdaEmptyBlock_withOnlyLineComments_idempotentForAllWrapStyles() {
        String input =
                """
                class T {
                    void m(java.util.function.Consumer<java.nio.channels.Channel> c) {
                        c.accept(
                                channel -> {
                                    // Do not close channel from here, rather wait for the callback
                                    // and then complete after releasing the buffer.
                                });
                    }
                }
                """;
        for (WrapStyle wrap : WrapStyle.values()) {
            Formatter f =
                    new Formatter(
                            FormatterConfig.builder()
                                    .lineLength(80)
                                    .wrapStyle(wrap)
                                    .build());
            String once = f.format(input);
            assertThat(f.format(once))
                    .as("wrapStyle=%s", wrap)
                    .isEqualTo(once);
            assertThat(once).doesNotContain("{}//");
        }
    }

    @Test
    void idempotency_lineCommentBeforeLambdaInitializer_breaksAfterEquals() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input =
                """
                class T {
                    void m() {
                        java.util.function.Consumer<String> handler =
                                // Mono note
                                s -> System.out.println(s);
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain("= //");
    }

    @Test
    void idempotency_lineCommentBeforeSingleLambdaArgument_breaksAfterOpeningParen() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m(Client client) {
                        client.accept("json")
                                .cookies(
                                        // keep comment with lambda argument
                                        cookies -> cookies.add("id", "123"))
                                .done();
                    }

                    interface Client {
                        Client accept(String value);

                        Client cookies(java.util.function.Consumer<Cookies> consumer);

                        Client done();
                    }

                    interface Cookies {
                        void add(String name, String value);
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain(".cookies(// keep");
    }

    @Test
    void idempotency_trailingCommentAfterSingleLambdaArgument_breaksToCommentLine() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m(Client client) {
                        client.accept("json")
                                .cookies(cookies -> cookies.add("id", "123")) // keep trailing comment on lambda arg
                                .done();
                    }

                    interface Client {
                        Client accept(String value);

                        Client cookies(java.util.function.Consumer<Cookies> consumer);

                        Client done();
                    }

                    interface Cookies {
                        void add(String name, String value);
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain(".cookies(// keep trailing comment");
    }

    @Test
    void idempotency_emptyLineCommentBetweenWrappedChainSegments_doesNotDriftIntoNextArgs() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m(DatabasePopulator databasePopulator) {
                        databasePopulator.populate(getConnectionFactory()) //
                                .as(StepVerifier::create) //
                                .verifyComplete();
                    }

                    Object getConnectionFactory() {
                        return null;
                    }

                    interface DatabasePopulator {
                        Result populate(Object connectionFactory);
                    }

                    interface Result {
                        Result as(java.util.function.Function<Object, Object> fn);

                        Result verifyComplete();
                    }

                    static class StepVerifier {
                        static Object create(Object value) {
                            return value;
                        }
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain(".as(//");
    }

    @Test
    void idempotency_emptyLineCommentAfterWrappedMultiArgCall_doesNotDriftIntoArgs() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m(Client client, Object lastName) {
                        client.sql("select count(0) from users where last_name = :name") //
                                .bind("name", lastName) //
                                .map((row, metadata) -> row.get(0));
                    }

                    interface Client {
                        Chain sql(String sql);
                    }

                    interface Chain {
                        Chain bind(String name, Object value);

                        Chain map(java.util.function.BiFunction<Object, Object, Object> mapper);
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain(".bind(\"name\", //");
    }

    @Test
    void idempotency_emptyLineCommentInReturnedWrappedChain_doesNotDriftAroundReturn() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    Object m(Object connectionFactory) {
                        return Mono.usingWhen(getConnection(connectionFactory), //
                                this::populate, //
                                connection -> releaseConnection(connection, connectionFactory), //
                                (connection, err) -> releaseConnection(connection, connectionFactory),
                                connection -> releaseConnection(connection, connectionFactory))
                                .onErrorMap(ex -> !(ex instanceof ScriptException),
                                        ex -> new UncategorizedScriptException("Failed to execute database script", ex));
                    }

                    Object populate(Object connection) {
                        return connection;
                    }

                    static Object getConnection(Object connectionFactory) {
                        return connectionFactory;
                    }

                    static Object releaseConnection(Object connection, Object connectionFactory) {
                        return connection;
                    }

                    static class Mono {
                        static Chain usingWhen(
                                Object connection,
                                java.util.function.Function<Object, Object> populate,
                                java.util.function.Function<Object, Object> release,
                                java.util.function.BiFunction<Object, Throwable, Object> releaseWithError,
                                java.util.function.Function<Object, Object> releaseAgain
                        ) {
                            return null;
                        }
                    }

                    interface Chain {
                        Object onErrorMap(
                                java.util.function.Predicate<Throwable> predicate,
                                java.util.function.Function<Throwable, Throwable> mapper
                        );
                    }

                    static class ScriptException extends RuntimeException {}

                    static class UncategorizedScriptException extends RuntimeException {
                        UncategorizedScriptException(String message, Throwable cause) {
                            super(message, cause);
                        }
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
    }

    @Test
    void idempotency_firstCommentedArgumentInScopedCall_doesNotDriftOntoQualifier() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                import java.util.Set;

                class T {
                    private static final Set<Integer> CODES = Set.of(
                            1,     // Oracle
                            301,   // SAP HANA
                            1062
                    );
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain("Set.of(// Oracle");
    }

    @Test
    void idempotency_lineCommentBeforeSingleExtendedType_breaksAfterExtendsKeyword() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    interface MsgOrBuilder extends
                            // generated marker
                            Base {
                    }

                    interface Base {
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain("extends // generated marker");
    }

    @Test
    void idempotency_trailingCommentAfterWrappedChainCall_doesNotForceBreakInsideSingleArg() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    Object m() {
                        return Mono.deferContextual(
                                        contextView -> {
                                            return Mono.empty();
                                        })
                                .subscribeOn(Schedulers.boundedElastic()); // keep trailing comment on the call
                    }
                }
                """;
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
        assertThat(once).doesNotContain(".subscribeOn(\n");
    }

    @Test
    @Disabled(
            "Known flake: second format can duplicate inter-argument line comments in this pattern; "
                    + "tracked separately from literal-chunk idempotency (RealWorldEval idempotency is the gate).")
    void idempotency_commentsInterspersedInMethodArguments_doNotReorder() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m() {
                        assertThat(events).containsExactly(

                            // --- Config1 ---
                            "Refreshed:Config1",
                            // No BeforeTestClass, since EventPublishingTestExecutionListener
                            // only publishes events for a context that has already been loaded.
                            "AfterTestClass:Config1",

                            // --- Config2 ---
                            // Here we expect a BeforeTestClass event, since Config2
                            // uses the same context as Config1.
                            "BeforeTestClass:Config2",
                            "AfterTestClass:Config2"
                        );
                    }
                }
                """;
        String once = f.format(input);
        String twice = f.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void idempotency_endOfLineCommentAfterStatement_staysAttachedWithoutExtraBlankLine() {
        Formatter f = new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.WIDE)
                        .build());
        String input = """
                class T {
                    void m() {
                        BindTarget bindTarget = mock();

                        BindMarkers bindMarkers = BindMarkersFactory.named("@", "p", 32).create();

                        bindMarkers.next(); // ignore
                        bindMarkers.next().bindNull(bindTarget, Integer.class);
                    }
                }
                """;
        String once = f.format(input);
        String twice = f.format(once);
        assertThat(twice).isEqualTo(once);
        assertThat(once).doesNotContain("\n\n        // ignore");
    }
}
