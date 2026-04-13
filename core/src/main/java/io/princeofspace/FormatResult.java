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

    /** Formatted Java source (normalized line endings and layout per {@link io.princeofspace.model.FormatterConfig}). */
    record Success(String formattedSource) implements FormatResult {}

    /** Could not produce formatted output. */
    sealed interface Failure extends FormatResult permits ParseFailure, EmptyCompilationUnit {

        /** Message suitable for {@link FormatterException} or logging. */
        String message();
    }

    /**
     * JavaParser reported problems; typically invalid or incomplete syntax.
     *
     * @param problemMessages one entry per {@link com.github.javaparser.Problem}, usually {@link Object#toString()}
     */
    record ParseFailure(List<String> problemMessages) implements Failure {
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
}
