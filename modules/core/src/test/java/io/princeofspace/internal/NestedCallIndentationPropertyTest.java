package io.princeofspace.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.TooManyMethods"})
@SuppressFBWarnings(
        value = "VA_FORMAT_STRING_USES_NEWLINE",
        justification = "Java text blocks + String.formatted; same as CanonicalRulesPropertyTest")
class NestedCallIndentationPropertyTest {

    private static final String ROOT_CALL = "rootSink";

    @Property(tries = 250)
    void wrappedStatements_keepContinuationIndentPositive_andCloserNotLeftOfOpeningStatement(
            @ForAll("generatedCases") GeneratedCase testCase) {
        Formatter formatter = formatter(testCase.closingParenOnNewLine);
        String formatted = formatter.format(testCase.source);

        int openingLineIndex = findLineIndexContaining(formatted, ROOT_CALL + "(");
        String[] lines = formatted.split("\\R", -1);
        int openingIndent = leadingSpaces(lines[openingLineIndex]);

        int statementEnd = findStatementEndLine(lines, openingLineIndex);
        assertThat(statementEnd)
                .as("statement for %s should end after opening line", testCase.description)
                .isGreaterThan(openingLineIndex);

        for (int i = openingLineIndex + 1; i <= statementEnd; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int indent = leadingSpaces(line);
            assertThat(indent)
                    .as("line %d in %s should have positive indentation", i + 1, testCase.description)
                    .isGreaterThan(0);

            String trimmed = line.trim();
            if (trimmed.endsWith(");")
                    || trimmed.equals(")")
                    || trimmed.equals("));")
                    || trimmed.equals(")));")
                    || trimmed.equals(")))));")) {
                assertThat(indent)
                        .as("closing delimiter line %d in %s must not be left of opening statement", i + 1, testCase.description)
                        .isGreaterThanOrEqualTo(openingIndent);
            }
        }
    }

    @Provide
    Arbitrary<GeneratedCase> generatedCases() {
        Arbitrary<Integer> depth = Arbitraries.integers().between(1, 4);
        Arbitrary<Integer> argCount = Arbitraries.integers().between(5, 8);
        Arbitrary<Boolean> ternary = Arbitraries.of(true, false);
        Arbitrary<Boolean> lineSeparated = Arbitraries.of(true, false);
        Arbitrary<Boolean> closingParenOnNewLine = Arbitraries.of(true, false);
        return Combinators.combine(depth, argCount, ternary, lineSeparated, closingParenOnNewLine)
                .as(this::buildCase);
    }

    private GeneratedCase buildCase(
            int depth, int argCount, boolean ternary, boolean lineSeparated, boolean closingParenOnNewLine) {
        String expression = ternary
                ? buildTernaryNestedCall(depth, argCount, lineSeparated)
                : buildNestedCall(depth, argCount, lineSeparated);
        String source =
                """
                class C {
                    Object m() {
                        return %s(%s);
                    }
                }
                """.formatted(ROOT_CALL, expression);
        String description = "depth=%d args=%d ternary=%s lineSeparated=%s closeNewLine=%s"
                .formatted(depth, argCount, ternary, lineSeparated, closingParenOnNewLine);
        return new GeneratedCase(source, closingParenOnNewLine, description);
    }

    private static String buildNestedCall(int depth, int argCount, boolean lineSeparated) {
        String expr = buildCallWithArgs(callName(depth), argCount);
        for (int d = depth - 1; d >= 1; d--) {
            String join = lineSeparated ? "\n                                " : "";
            expr = callName(d) + "(" + join + expr + ")";
        }
        return expr;
    }

    private static String buildTernaryNestedCall(int depth, int argCount, boolean lineSeparated) {
        String thenExpr = buildNestedCall(depth, argCount, lineSeparated);
        String elseExpr = buildNestedCall(Math.max(1, depth - 1), argCount, lineSeparated);
        return "veryLongConditionExpressionForCheck ? " + thenExpr + " : " + elseExpr;
    }

    private static String callName(int depth) {
        return "innerMethod" + depth + "WithLongIdentifier";
    }

    private static String buildCallWithArgs(String callName, int argCount) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            args.add(("longArgumentName" + i + "_for_" + callName).toLowerCase(Locale.ROOT));
        }
        return callName + "(" + String.join(", ", args) + ")";
    }

    private static Formatter formatter(boolean closingParenOnNewLine) {
        return new Formatter(
                FormatterConfig.builder()
                        .lineLength(80)
                        .wrapStyle(WrapStyle.BALANCED)
                        .closingParenOnNewLine(closingParenOnNewLine)
                        .indentSize(4)
                        .javaLanguageLevel(JavaLanguageLevel.of(17))
                        .build());
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static int findLineIndexContaining(String text, String needle) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        throw new AssertionError("Missing line containing: " + needle);
    }

    private static int findStatementEndLine(String[] lines, int openingLineIndex) {
        for (int i = openingLineIndex; i < lines.length; i++) {
            if (lines[i].trim().endsWith(";")) {
                return i;
            }
        }
        throw new AssertionError("Missing statement terminator after opening line index " + openingLineIndex);
    }

    private static final class GeneratedCase {
        final String source;
        final boolean closingParenOnNewLine;
        final String description;

        GeneratedCase(String source, boolean closingParenOnNewLine, String description) {
            this.source = source;
            this.closingParenOnNewLine = closingParenOnNewLine;
            this.description = description;
        }
    }
}
