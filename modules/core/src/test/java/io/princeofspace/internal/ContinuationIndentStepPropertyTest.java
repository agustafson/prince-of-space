package io.princeofspace.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encodes a tightening of Rule 3: a single continuation step must not exceed {@code 2 * indentSize}
 * spaces when comparing consecutive non-blank lines in formatted output (no stacking two continuation
 * jumps without an intervening line break that establishes a new baseline).
 *
 * <p>This catches parenthesized lambdas where formal parameters use one continuation step from the
 * {@code (} line but the closing {@code ) ->} line used an extra {@code indentSize} (showroom
 * scenario 44 around the {@code ) ->} line).
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@SuppressFBWarnings(
        value = "VA_FORMAT_STRING_USES_NEWLINE",
        justification = "Java text blocks; SpotBugs text block false positive")
class ContinuationIndentStepPropertyTest {

    private static final String IDENTIFIER_FORALL = "identifier";

    /**
     * Between any two consecutive non-blank lines, an increase in leading space count must not
     * exceed {@code 2 * indentSize}. This matches Rule 3's single continuation unit ({@code 2 *
     * indentSize}) as the maximum <em>per line transition</em> unless a narrower Rule 7 chain step
     * ({@code indentSize}) applies — chain steps are smaller and therefore allowed.
     */
    @Property(tries = 120)
    void rule3_singleLineIndentIncrease_neverExceedsTwoIndentSteps(
            @ForAll(IDENTIFIER_FORALL) String identifier,
            @ForAll("indentSizes") int indentSize,
            @ForAll("closingParenOnNewLine") boolean closingParenOnNewLine) {
        String source = parenthesizedLambdaBiFunctionSource(identifier);
        Formatter formatter = formatter(72, WrapStyle.BALANCED, closingParenOnNewLine, indentSize, JavaLanguageLevel.of(21));
        String once = formatter.format(source);
        String twice = formatter.format(once);
        assertThat(twice).as("idempotency").isEqualTo(once);

        assertConsecutiveIndentIncreasesBounded(once, indentSize);
    }

    /**
     * Regression shape matching showroom scenario 44: wrapped generic {@link java.util.function.BiFunction}
     * with line-broken type arguments, assignment RHS starts with {@code (} on its own line, long
     * parameter lines, then {@code ) -> ...}. The closing {@code ) ->} line must align one {@link
     * FormatterConfig#indentSize()} step past the standalone {@code '('} line (not an extra {@code
     * indentSize} deeper than that baseline).
     */
    @Property(tries = 80)
    void rule3_parenthesizedLambdaClosing_paren_arrow_aligns_openParen_plus_indentUnit(
            @ForAll(IDENTIFIER_FORALL) String identifier,
            @ForAll("indentSizes") int indentSize,
            @ForAll("closingParenOnNewLine") boolean closingParenOnNewLine) {
        String source = parenthesizedLambdaBiFunctionSource(identifier);
        Formatter formatter = formatter(72, WrapStyle.BALANCED, closingParenOnNewLine, indentSize, JavaLanguageLevel.of(21));
        String out = formatter.format(source);
        assertThat(formatter.format(out)).as("idempotency").isEqualTo(out);

        String[] lines = out.split("\\R", -1);
        int closeArrowLine = indexOfLineContaining(lines, ") ->");
        assertThat(closeArrowLine)
                .as(
                        "expected ') ->' line in formatted output%n%s",
                        excerpt(lines, 0, Math.min(lines.length, 40)))
                .isGreaterThanOrEqualTo(0);

        int openParenLine = indexOfStandaloneOpenParenLineBefore(lines, closeArrowLine);
        assertThat(openParenLine)
                .as(
                        "expected '(' line before ') ->' in formatted output%n%s",
                        excerpt(lines, 0, Math.min(lines.length, 40)))
                .isGreaterThanOrEqualTo(0);

        int openIndent = leadingSpaces(lines[openParenLine]);
        int closeIndent = leadingSpaces(lines[closeArrowLine]);
        assertThat(closeIndent)
                .as(
                        "') ->' indent must equal '(' line indent + indentSize (%d); see showroom Java 25 scenario 44",
                        indentSize)
                .isEqualTo(openIndent + indentSize);
    }

    private static void assertConsecutiveIndentIncreasesBounded(String formatted, int indentSize) {
        String[] lines = formatted.split("\\R", -1);
        int maxStep = 2 * indentSize;
        int prevNonBlank = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (prevNonBlank >= 0) {
                int a = leadingSpaces(lines[prevNonBlank]);
                int b = leadingSpaces(lines[i]);
                if (b > a) {
                    assertThat(b - a)
                            .as(
                                    "indent jump from line %d to %d must be <= %d (2 * indentSize)",
                                    prevNonBlank + 1,
                                    i + 1,
                                    maxStep)
                            .isLessThanOrEqualTo(maxStep);
                }
            }
            prevNonBlank = i;
        }
    }

    private static String parenthesizedLambdaBiFunctionSource(String identifier) {
        return """
                import java.util.Map;
                import java.util.Optional;
                import java.util.concurrent.CompletableFuture;
                import java.util.function.BiFunction;

                class C_%s {
                    void m() {
                        BiFunction<
                                Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
                                Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
                                Integer> cmp_%s =
                                (
                                                Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> left_%s,
                                                Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> right_%s
                                        ) -> left_%s.getKey().compareTo(right_%s.getKey());
                    }
                }
                """
                .formatted(identifier, identifier, identifier, identifier, identifier, identifier);
    }

    @Provide
    Arbitrary<String> identifier() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(4).ofMaxLength(8);
    }

    /**
     * Rule 3 ties continuation to {@code 2 * indentSize}; indent sizes outside {@code {4, 8}} are omitted until
     * non-default indent sizes are exercised consistently in integration tests.
     */
    @Provide
    Arbitrary<Integer> indentSizes() {
        return Arbitraries.of(4, 8);
    }

    @Provide
    Arbitrary<Boolean> closingParenOnNewLine() {
        return Arbitraries.of(true, false);
    }

    private static Formatter formatter(
            int lineLength,
            WrapStyle wrapStyle,
            boolean closeOnNewLine,
            int indentSize,
            JavaLanguageLevel languageLevel) {
        return new Formatter(
                FormatterConfig.builder()
                        .lineLength(lineLength)
                        .wrapStyle(wrapStyle)
                        .closingParenOnNewLine(closeOnNewLine)
                        .indentSize(indentSize)
                        .javaLanguageLevel(languageLevel)
                        .build());
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static int indexOfLineContaining(String[] lines, String needle) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the nearest line above {@code beforeIndex} whose trimmed text is exactly {@code "("} — the
     * showroom layout for a parenthesized lambda whose parameter list is wrapped.
     */
    private static int indexOfStandaloneOpenParenLineBefore(String[] lines, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            if (lines[i].trim().equals("(")) {
                return i;
            }
        }
        return -1;
    }

    private static String excerpt(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(String.format("%4d|%s%n", i + 1, lines[i]));
        }
        return sb.toString();
    }
}
