package io.princeofspace;

import java.util.List;

/**
 * Outcome of {@link io.princeofspace.internal.FormattingEngine}'s format attempt. Use
 * {@link Formatter#formatResult(String)} for a
 * non-throwing API; {@link Formatter#format(String)} maps failures to {@link FormatterException}.
 *
 * <p>Failures are modeled as a sealed hierarchy so callers can handle parse errors distinctly from
 * other pipeline issues.
 */
public sealed interface FormatResult permits FormatResult.Success, FormatResult.Failure {

    /**
     * Formatted Java source.
     *
     * @param formattedSource normalized source text laid out per {@link io.princeofspace.model.FormatterConfig}
     */
    record Success(String formattedSource) implements FormatResult {}

    /** Could not produce formatted output. */
    sealed interface Failure extends FormatResult permits ParseFailure, EmptyCompilationUnit, NonConvergent {

        /**
         * Returns a human-readable failure message suitable for {@link FormatterException} or logging.
         *
         * @return failure message text
         */
        String message();
    }

    /**
     * JavaParser reported problems; typically invalid or incomplete syntax.
     *
     * @param problemMessages one entry per {@link com.github.javaparser.Problem}, usually {@link Object#toString()}
     */
    record ParseFailure(List<String> problemMessages) implements Failure {
        /** Creates an immutable parse-failure payload from parser problem messages. */
        public ParseFailure {
            problemMessages = List.copyOf(problemMessages);
        }

        @Override
        public String message() {
            return "Parse failed:\n" + String.join("\n", problemMessages);
        }
    }

    /** Parse succeeded structurally but the parser supplied no {@link com.github.javaparser.ast.CompilationUnit}. */
    record EmptyCompilationUnit() implements Failure {
        @Override
        public String message() {
            return "Parser returned no compilation unit";
        }
    }

    /**
     * Formatting changed output on each pass and did not reach a fixed point within the configured pass budget.
     *
     * @param passesAttempted number of format passes performed
     */
    record NonConvergent(int passesAttempted) implements Failure {
        @Override
        public String message() {
            return "Formatting did not converge to a fixed point within " + passesAttempted + " pass(es)";
        }
    }
}
