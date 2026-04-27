package io.princeofspace;

import io.princeofspace.internal.FormattingEngine;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Main entry point for the Prince of Space formatter.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Formatter formatter = new Formatter(FormatterConfig.defaults());
 * String formatted = formatter.format(sourceCode);
 * }</pre>
 *
 * <p>For a non-throwing API, use {@link #formatResult(String)} and pattern-match on {@link FormatResult}.
 */
public final class Formatter {

    private final FormattingEngine engine;

    /**
     * Creates a formatter with the provided configuration.
     *
     * @param config formatting options; must not be {@code null}
     */
    public Formatter(FormatterConfig config) {
        this.engine = new FormattingEngine(requireNonNull(config, "config"));
    }

    /**
     * Attempts to format the given Java source without throwing. Failures are {@link FormatResult.Failure}
     * variants with structured detail (e.g. {@link FormatResult.ParseFailure}).
     *
     * @param sourceCode the Java source to format
     * @return success with formatted text, or a typed failure
     */
    public FormatResult formatResult(String sourceCode) {
        return engine.format(sourceCode);
    }

    /**
     * Formats the given Java source code.
     *
     * @param sourceCode the Java source to format
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed or the pipeline cannot produce output
     */
    public String format(String sourceCode) {
        FormatResult result = engine.format(sourceCode);
        if (result instanceof FormatResult.Success success) {
            return success.formattedSource();
        }
        // FormatResult is sealed: Success | Failure — the cast is exhaustive.
        throw new FormatterException(((FormatResult.Failure) result).message());
    }

    /**
     * Formats the given Java source code, prefixing failure messages with {@code filePath}.
     *
     * @param sourceCode the Java source to format
     * @param filePath path to the file, used only for diagnostics
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed or the pipeline cannot produce output
     */
    public String format(String sourceCode, Path filePath) {
        FormatResult result = engine.format(sourceCode);
        if (result instanceof FormatResult.Success success) {
            return success.formattedSource();
        }
        // FormatResult is sealed: Success | Failure — the cast is exhaustive.
        throw new FormatterException(filePath + ": " + ((FormatResult.Failure) result).message());
    }
}
