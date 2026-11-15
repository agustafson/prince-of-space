package io.princeofspace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Providers;
import com.github.javaparser.UnicodeEscapeProcessingProvider;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks comment count, order and syntax boundaries before convergence can hide a first-pass defect. */
class CommentBoundaryPreservationTest {

    private static final List<String> INPUTS = List.of(
            """
            class T {
                static final Object FIRST =
                    new Object(); // repeated
                static final long SECOND =
                    64L; // repeated
                int following;
            }
            """,
            """
            class T {
                void f() {
                    Object first =
                        new Object(); // repeated
                    Object second =
                        new Object(); // repeated
                }
            }
            """,
            """
            class T {
                void f(
                    // first parameter
                    Object first,
                    // second parameter
                    Object second) {}
            }
            """,
            """
            class T {
                void f(@Opt(value = "a" /* repeated */, other = "b" /* repeated */) String first,
                       @Opt("c" /* single value */) String second) {}
            }
            """,
            """
            class T {
                Object f() {
                    return startsWith("prefix").and(implementsInterface(namedOneOf( // opening
                        "a", "b")));
                }
            }
            """,
            """
            class T {
                void f() {
                    builder.with(new Object()).finish()
                    // .installOn(target)
                    ;
                }
            }
            """,
            """
            class T {
                Object f() {
                    return first().or(
                        // repeated
                        // repeated
                        // third
                        second().and(third()));
                }
            }
            """,
            """
            class T {
                void f() {
                    call(true, /* retry */
                        4, /* max */ // explanation
                        "host");
                }
            }
            """,
            """
            class T {
                void f() {
                    // spotless:off
                    Object headers = headers(
                        "k2", "v2", // encoded once
                        "k3", "v3", // encoded twice
                        "k4", "v4");
                    // spotless:on
                }
            }
            """,
            """
            class T {
                Object f() {
                    return first()
                        // first line
                        // second line
                        .second().third();
                }
            }
            """,
            """
            class T {
                Object value = HttpRequest.newBuilder()
                    .timeout(
                        Duration.ofSeconds(
                            1)) // prevents the idle timeout
                    // while responding in sync mode
                    .build();
            }
            """,
            """
            class T {
                Object value = builder()
                    .timeout(Duration.ofSeconds(1)) // repeated
                    // continued
                    .timeout(Duration.ofSeconds(2)) // repeated
                    // continued
                    .build();
            }
            """,
            """
            class T {
                Object f() { return foo(). // segment label
                    bar(); }
            }
            """,
            """
            class T {
                Object f() { return foo() /* before dot */ .bar(); }
            }
            """,
            """
            class T {
                Object f() { return foo(). /* inline label */ // segment label
                    bar(); }
            }
            """,
            """
            class T {
                Object f() { return foo() /* before dot */ . // generic segment
                    <String>bar(); }
            }
            """,
            """
            class T {
                Object f() { return foo().<String> // selector
                    bar(); }
            }
            """,
            """
            class T {
                Object f() { return foo().bar // arguments
                    (); }
            }
            """,
            """
            class T {
                Object f() { return foo(). // first
                    bar(). // second
                    baz(); }
            }
            """,
            """
            class T extends Base {
                void f(Object obj) {
                    obj. /* prefix */ // object selector
                        hashCode();
                    this.foo // this arguments
                        ( // nested argument
                            1);
                    super. // super selector
                        foo();
                    holder.value.foo // field arguments
                        ();
                }
            }
            """,
            """
            class T {
                void f() {
                    Object value =
                        // captured when subscribed
                        // propagated downstream
                        Mono.just("hello") //
                            // first stage
                            .flatMap(v -> traced(v))
                            .map(this::convert)
                            .doOnNext(this::consume);
                }
            }
            """,
            """
            class T {
                void f() {
                    call /* before opening */ (1,
                        // later argument
                        2);
                }
            }
            """
            ,
            """
            class T {
                void f(Object[] values, Object[] tainted) {
                    if ((values != null
                            && values.length
                                != 0) // ranges imply vulnerability
                        || tainted.length == 0 // no tainted ranges
                    ) { return; }
                }
            }
            """
            ,
            """
            class T {
                Object value = new Object() {
                    @Override public void run() {
                        // child heading
                        start(); // child suffix
                    }
                };
            }
            """,
            """
            class T {
                @SuppressForbidden // allow Class.forName()
                protected @Nullable String f() { return null; }
            }
            """,
            """
            class T {
                // field heading
                private static final int SPACE_CHARS = 2; // leading and trailing spaces
            }
            """
            ,
            """
            class T {
                String serviceType;
                void f(ConfigProvider configProvider) {
                    serviceType =
                        // installation type from the environment
                        configProvider.getString("instrumentation.install.type");
                }
            }
            """
            ,
            """
            class T {
                Object value = // field prefix
                    foo();
                void f() {
                    Object local = // local prefix
                        foo();
                    value = // assignment prefix
                        foo();
                    value += // compound assignment prefix
                        foo();
                }
            }
            """,
            """
            class T {
                final String ROOT = "org.apache.spark";
                Object values = new HashSet<>(Arrays.asList(
                    ROOT + ".sql.catalyst.optimizer.BuildSide", // enum (v3+)
                    ROOT + ".sql.catalyst.plans.JoinType", // enum
                    ROOT + ".sql.execution.exchange.ShuffleOrigin" // enum (v3+)
                ));
            }
            """,
            """
            class T {
                void f() {
                    use(
                    // first
                    // second
                    "before",
                    // first
                    // second
                    // third
                    "after");
                }
            }
            """
            ,
            """
            class T {
                @A("first")
                @A(
                    "second") // subtype of the first
                public static String f() { return null; }
            }
            """,
            """
            @A // class marker
            class T {
                @A // field marker
                String value;
                void f() {
                    @A // local marker
                    String local = "value";
                }
            }
            """
            ,
            """
            class T {
                Object f() { return call(
                    "a"); // return suffix
                }
                void g() { throw new IllegalStateException(
                    "message"); // throw suffix
                }
            }
            """,
            """
            class T {
                T() { this(
                    "a"); // constructor suffix
                }
                T(String value) {}
            }
            """,
            """
            class T extends Base {
                T() { super(
                    "a"); // super suffix
                }
            }
            """,
            """
            enum T {
                BINDING_ERROR(-127); // binding error
                T(int code) {}
            }
            """
            ,
            """
            class T {
                Object value = new Base() {
                    void m() {}
                    // after last
                };
            }
            """,
            """
            class T {
                Object value = new Base(/* no arguments */) { void m() {} };
            }
            """,
            """
            class T {
                Object value = new Base(1) { // anonymous heading
                    void m() {}
                };
            }
            """,
            """
            class T {
                Object value = new Base() {
                    // only body comment
                };
            }
            """
            ,
            """
            class T {
                void f() {
                    Object value = new Holder(1, // one
                        2, /* two */ // explanation
                        3);
                }
            }
            """,
            """
            class T {
                void f() {
                    Object first = new Holder(// opening
                        1);
                    Object second = new Holder(/* repeated */ /* repeated */);
                }
            }
            """,
            """
            class T {
                Object f() {
                    return builder.strictTraceWrites(true) // avoid redundant writes
                        .finish();
                }
            }
            """,
            """
            class T {
                void f() {
                    builder.call(true /* enabled */, 4 /* retries */); // statement suffix
                }
            }
            """
            ,
            """
            class T {
                @Test
                // setup
                @WithConfig(value = "") // blank string
                @WithConfig(value = " x ") // whitespace
                void f() {}
            }
            """,
            """
            class T {
                @Test
                // setup
                @WithConfig(value = "") // repeated
                @WithConfig(value = " x ") // repeated
                public String f() { return "ok"; }
            }
            """
            ,
            """
            class T {
                int f(int op) {
                    switch (op) {
                        case 0: return 1;
                        case 1:
                            // no change
                            // repeated
                            // repeated
                    }
                    return 0;
                }
            }
            """
            ,
            """
            class T {
                int f(int op) {
                    switch (op) {
                        case 1:
                            return 1;
                            // no default case
                    }
                    return 0;
                }
            }
            """,
            """
            class T {
                void f(int tag) {
                    switch (tag) {
                        default: {
                            foo();
                            break;
                        } // default:
                    } // switch (tag)
                }
            }
            """,
            """
            class T {
                Object f(int op) {
                    return switch (op) {
                        default -> foo();
                            // final expression case
                    };
                }
            }
            """,
            """
            class T {
                boolean f(int b) {
                    if (b == 0x25 /* % */) { return true; }
                    return b >= 0x30 /* 0 */ && b <= 0x39 /* 9 */;
                }
            }
            """,
            """
            class T {
                boolean f(String className) {
                    return className != null && /* do not insert a probe */ !className.isEmpty();
                }
            }
            """,
            """
            class T {
                Object f() {
                    return first().and(
                        /* only if previous did not match */
                        hasNoDeclaredMethod(isTraitMethod()));
                }
            }
            """,
            """
            class T {
                Object f() {
                    return first().or(
                        // only
                        second().and(third()));
                }
            }
            """
    );

    @ParameterizedTest
    @CsvSource({"WIDE, true", "NARROW, true", "BALANCED, true", "WIDE, false", "NARROW, false", "BALANCED, false"})
    void commentsKeepTheirSourceBoundaries(WrapStyle style, boolean closingParenOnNewLine) {
        for (IndentStyle indent : IndentStyle.values()) {
            FormatterConfig config = FormatterConfig.builder()
                    .indentSize(2)
                    .lineLength(100)
                    .wrapStyle(style)
                    .closingParenOnNewLine(closingParenOnNewLine)
                    .indentStyle(indent)
                    .javaLanguageLevel(JavaLanguageLevel.of(25))
                    .build();
            Formatter firstPass = new Formatter(config, true);
            Formatter formatter = new Formatter(config);
            for (boolean unicode : new boolean[] {false, true}) {
                for (String newline : new String[] {"\n", "\r\n"}) {
                    for (String fixture : INPUTS) {
                        String input = unicode
                                ? fixture.replace("class T {", "class T { String prefix = \"\\" + "uuuu0041\";")
                                : fixture;
                        input = input.replace("\n", newline);
                        List<Boundary> expected = commentBoundaries(input);
                        String once = firstPass.format(input);
                        assertThat(once.lines()).noneMatch(line -> line.endsWith(" ") || line.endsWith("\t"));
                        assertThat(commentBoundaries(once)).as("First pass for %s/%s:%n%s", style, indent, input)
                                .containsExactlyElementsOf(expected);
                        String stable = formatter.format(input);
                        assertThat(stable.lines()).noneMatch(line -> line.endsWith(" ") || line.endsWith("\t"));
                        assertThat(commentBoundaries(stable)).as("Final output for %s/%s:%n%s", style, indent, input)
                                .containsExactlyElementsOf(expected);
                        assertThat(formatter.format(stable)).isEqualTo(stable);
                    }
                }
            }
        }
    }

    @Test
    void statementSuffixStaysOutsideNestedArguments() {
        String input = """
                class T {
                    void f(long index) {
                        ((Specification) value)
                            .setIndex((int) index); // statement suffix
                        next();
                    }
                }
                """;
        FormatterConfig config = FormatterConfig.defaults();
        for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
            String output = formatter.format(input);
            assertThat(output).containsOnlyOnce("// statement suffix");
            var unit = new JavaParser().parse(output).getResult().orElseThrow();
            var marker = unit.getAllContainedComments().stream()
                    .filter(comment -> comment.getContent().contains("statement suffix"))
                    .findFirst().orElseThrow().getBegin().orElseThrow();
            assertThat(unit.findAll(MethodCallExpr.class)).noneMatch(call -> call.getRange().orElseThrow().contains(marker));
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    private static List<Boundary> commentBoundaries(String source) {
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        var parsed = parser.parse(ParseStart.COMPILATION_UNIT,
                new UnicodeEscapeProcessingProvider(Providers.provider(source)));
        assertThat(parsed.isSuccessful()).as("Parse %s:%n%s", parsed.getProblems(), source).isTrue();
        var unit = parsed.getResult().orElseThrow();
        List<Boundary> boundaries = new ArrayList<>();
        for (JavaToken token : unit.getTokenRange().orElseThrow()) {
            if (!token.getCategory().isComment()) {
                continue;
            }
            JavaToken previous = token;
            while (previous.getPreviousToken().isPresent()) {
                previous = previous.getPreviousToken().orElseThrow();
                if (!previous.getCategory().isWhitespaceOrComment()) {
                    break;
                }
            }
            JavaToken next = token;
            while (next.getNextToken().isPresent()) {
                next = next.getNextToken().orElseThrow();
                if (!next.getCategory().isWhitespaceOrComment()) {
                    break;
                }
            }
            String declaration = token.getText().startsWith("//")
                    ? declarationOccurrence(unit, token, previous, next) : "";
            boundaries.add(declaration.isEmpty()
                    ? new Boundary(codeWindow(token, true), token.getText(), codeWindow(token, false))
                    : new Boundary("declaration", token.getText(), declaration));
        }
        return boundaries;
    }

    private static String codeWindow(JavaToken token, boolean before) {
        List<String> tokens = new ArrayList<>();
        var neighbour = before ? token.getPreviousToken() : token.getNextToken();
        while (neighbour.isPresent() && tokens.size() < 3) {
            JavaToken current = neighbour.orElseThrow();
            if (!current.getCategory().isWhitespaceOrComment()) {
                if (before) {
                    tokens.add(0, current.getText());
                } else {
                    tokens.add(current.getText());
                }
            }
            neighbour = before ? current.getPreviousToken() : current.getNextToken();
        }
        return tokens.toString();
    }

    private static String declarationOccurrence(CompilationUnit unit, JavaToken comment, JavaToken previous, JavaToken next) {
        List<Node> declarations = unit.findAll(Node.class, node -> node instanceof FieldDeclaration
                || node instanceof ExpressionStmt || node instanceof ReturnStmt || node instanceof ThrowStmt
                || node instanceof ExplicitConstructorInvocationStmt);
        for (int i = 0; i < declarations.size(); i++) {
            Node declaration = declarations.get(i);
            boolean trailing = previous.getText().equals(";")
                    && declaration.getEnd().equals(previous.getRange().map(range -> range.end))
                    && comment.getRange().orElseThrow().begin.line == previous.getRange().orElseThrow().end.line;
            boolean leading = declaration.getBegin().equals(next.getRange().map(range -> range.begin));
            if (trailing || leading) {
                String type = declaration instanceof ExpressionStmt statement
                        ? statement.getExpression().getClass().getSimpleName() : declaration.getClass().getSimpleName();
                return type + "#" + i;
            }
        }
        return "";
    }

    @ParameterizedTest
    @CsvSource({"WIDE, true", "NARROW, true", "BALANCED, true", "WIDE, false", "NARROW, false", "BALANCED, false"})
    void wrappedAnnotationValuesKeepInlineComments(WrapStyle style, boolean closingParenOnNewLine) {
        FormatterConfig config = FormatterConfig.builder().indentSize(2).lineLength(40).wrapStyle(style)
                .closingParenOnNewLine(closingParenOnNewLine).build();
        for (String input : List.of(
                "class T { @SuppressWarnings(\"aaaaaaaaaaaaaaaaaaaa\" /* why */) void f() {} }",
                "class T { @SuppressWarnings(/* why */ \"aaaaaaaaaaaaaaa\") int field; }",
                "class T { void f(@SuppressWarnings(\"aaaaaaaaaaaaaaa\" /* why */) String value) {} }")) {
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                String output = formatter.format(input);
                assertThat(commentBoundaries(output)).containsExactlyElementsOf(commentBoundaries(input));
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    private record Boundary(String previous, String comment, String next) {}

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void binaryOperandLabelsKeepNestedArgumentAndLambdaComments(WrapStyle style) {
        for (int width : new int[] {50, 100}) {
            FormatterConfig config = FormatterConfig.builder().lineLength(width).wrapStyle(style).build();
            for (String input : List.of(
                    "class T { String f(Object x) { return \"p\" + /* label */ String.format(\"%s\", // why\n x); } }",
                    "class T { boolean f() { return enabledForRequest && /* guard */ check(items, // keep\n false); } }",
                    "class T { Object f() { return \"p\" + /* label */ obj.foo // selector\n (1); } }",
                    "class T { Object f() { return \"p\" + /* label */ transform(value -> { // nested\n return value; }); } }")) {
                for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                    String output = formatter.format(input);
                    assertThat(commentBoundaries(output)).as("%s/%s:%n%s", style, width, input)
                            .containsExactlyElementsOf(commentBoundaries(input));
                }
                Formatter formatter = new Formatter(config);
                String output = formatter.format(input);
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    @Test
    void arrowCaseSuffixStaysAfterItsStatement() {
        String input = "class T { void f(int op) { switch (op) { case 1 -> foo(); // suffix\n default -> bar(); } } }";
        FormatterConfig config = FormatterConfig.builder().indentSize(2).build();
        for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
            String output = formatter.format(input);
            assertThat(output).contains("case 1 -> foo(); // suffix\n");
        }
        Formatter formatter = new Formatter(config);
        String stable = formatter.format(input);
        assertThat(formatter.format(stable)).isEqualTo(stable);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void longLiteralConcatenationRetainsInterOperandComments(WrapStyle style) {
        List<String> values = IntStream.range(0, 128).mapToObj(index -> "piece" + index).toList();
        List<String> pieces = new ArrayList<>(values.stream().map(value -> "\"" + value + "\"").toList());
        pieces.set(64, pieces.get(64) + " /* retained */");
        String input = "class T { String value = " + String.join(" + ", pieces) + "; }";
        FormatterConfig config = FormatterConfig.builder().indentSize(2).lineLength(40).wrapStyle(style).build();
        for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
            String output = formatter.format(input);
            assertThat(output).containsOnlyOnce("/* retained */");
            assertThat(new JavaParser().parse(output).getResult().orElseThrow().findAll(StringLiteralExpr.class))
                    .extracting(StringLiteralExpr::asString).containsExactlyElementsOf(values);
        }
        Formatter formatter = new Formatter(config);
        String stable = formatter.format(input);
        assertThat(formatter.format(stable)).isEqualTo(stable);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void inlineConcatLabelParticipatesInWrapping(WrapStyle style) {
        FormatterConfig config = FormatterConfig.builder().indentSize(2).lineLength(40).wrapStyle(style).build();
        String input = "class T { String value = \"abcdefgh\" /* label */ + \"ijklmnop\"; }";
        Formatter formatter = new Formatter(config);
        String output = formatter.format(input);
        assertThat(output).containsOnlyOnce("/* label */");
        assertThat(output.lines()).allMatch(line -> line.length() <= 40);
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void opaqueConcatOperandIncludesItsInlineCommentWidth(WrapStyle style) {
        FormatterConfig config = FormatterConfig.builder().lineLength(2200).wrapStyle(style).build();
        for (int marker : new int[] {0, 64, 126}) {
            List<String> pieces = new ArrayList<>(Collections.nCopies(128, "\"abcdefghijkl\""));
            pieces.set(marker, pieces.get(marker) + " /* retained */");
            String input = "class T { String s = " + String.join(" + ", pieces) + "; }";
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                String output = formatter.format(input);
                assertThat(output.lines()).allMatch(line -> line.length() <= 2200);
                assertThat(output).containsOnlyOnce("/* retained */");
                assertThat(new JavaParser().parse(output).getResult().orElseThrow().findAll(StringLiteralExpr.class))
                        .hasSize(pieces.size()).allSatisfy(literal -> assertThat(literal.asString()).isEqualTo("abcdefghijkl"));
            }
            Formatter formatter = new Formatter(config);
            String output = formatter.format(input);
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @CsvSource({"WIDE, true", "NARROW, true", "BALANCED, true", "WIDE, false", "NARROW, false", "BALANCED, false"})
    void commaSuffixCommentsStayWithTheirRows(WrapStyle style, boolean closingParenOnNewLine) {
        String input = """
                class T {
                    void f() {
                        headers("k2", "v2", // encoded once
                            "k3", "v3", /* double */ // encoded twice
                            "k4", "v4");
                    }
                }
                """;
        for (int width : new int[] {40, 100}) {
            FormatterConfig config = FormatterConfig.builder().indentSize(2).lineLength(width).wrapStyle(style)
                    .closingParenOnNewLine(closingParenOnNewLine).build();
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                String output = formatter.format(input);
                assertThat(output).contains("\"v2\", // encoded once\n", "\"v3\", /* double */ // encoded twice\n");
                assertThat(output.lines()).noneMatch(line -> line.endsWith(" ") || line.endsWith("\t"));
                assertThat(commentBoundaries(output)).containsExactlyElementsOf(commentBoundaries(input));
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    @Test
    void returnTypeAnnotationsSurviveCommentReflow() {
        FormatterConfig config = FormatterConfig.builder().indentSize(2).lineLength(100).build();
        for (String input : List.of(
                "class T { @SuppressForbidden // allow Class.forName()\n protected @Nullable String f() { return null; } }",
                "class T { protected // allow Class.forName()\n @Nullable String f() { return null; } }")) {
            for (Formatter formatter : List.of(new Formatter(config, true), new Formatter(config))) {
                String output = formatter.format(input);
                assertThat(output).containsOnlyOnce("@Nullable").containsOnlyOnce("// allow Class.forName()");
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    @Test
    void declarationSuffixGroupsStayOnTheirLineWithoutBlankLines() {
        FormatterConfig config = FormatterConfig.builder().indentSize(2).build();
        Formatter formatter = new Formatter(config);
        for (String input : List.of(
                "class T { int a = 1; /* a */ // b\n int c; }",
                "class T { void f() { int a = 1; /* a */ // b\n int c; } }")) {
            for (Formatter pass : List.of(new Formatter(config, true), formatter)) {
                String output = pass.format(input);
                assertThat(output).contains("int a = 1; /* a */ // b\n").doesNotContain("\n\n");
                assertThat(output).containsOnlyOnce("/* a */").containsOnlyOnce("// b");
                assertThat(pass.format(output)).isEqualTo(output);
            }
        }
        String fields = """
                class T {
                  // marker
                  Object value = new Object();
                  int following;
                }
                """;
        String locals = """
                class T {
                  void f() {
                    // marker
                    Object value = new Object();
                    int following;
                  }
                }
                """;
        for (Formatter pass : List.of(new Formatter(config, true), formatter)) {
            assertThat(pass.format("class T { Object value =\n new Object(); // marker\n int following; }"))
                    .isEqualTo(fields);
            assertThat(pass.format("class T { void f() { Object value =\n new Object(); // marker\n int following; } }"))
                    .isEqualTo(locals);
            assertThat(pass.format(fields)).isEqualTo(fields);
            assertThat(pass.format(locals)).isEqualTo(locals);
        }
    }
}
