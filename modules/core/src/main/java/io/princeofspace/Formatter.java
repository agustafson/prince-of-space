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
 *
 * <p>A single {@code Formatter} instance is safe to share across threads. Each thread receives its
 * own {@link FormattingEngine} via {@link ThreadLocal}, so JavaParser and the pretty printer (both
 * stateful and not thread-safe) are never accessed concurrently.
 */
public final class Formatter {

    private static final String SOURCE_CODE_PARAM = "sourceCode";
    private static final String FILE_PATH_PARAM = "filePath";

    private final FormatterConfig config;
    private final ThreadLocal<FormattingEngine> engine;

    /**
     * Creates a formatter with the provided configuration.
     *
     * @param config formatting options; must not be {@code null}
     */
    public Formatter(FormatterConfig config) {
        this(config, false);
    }

    /**
     * Creates a formatter with optional fast single-pass formatting (no convergence loop).
     *
     * <p>{@code fastSinglePass=true} is intended for throughput experiments (for example the
     * {@code :formatter-benchmark} module). The default {@link Formatter#Formatter(FormatterConfig)}
     * remains strict fixed-point formatting for correct idempotent output in the general case.
     *
     * @param config formatting options; must not be {@code null}
     * @param fastSinglePass when {@code true}, format once per call without proving a fixed point
     */
    public Formatter(FormatterConfig config, boolean fastSinglePass) {
        this.config = requireNonNull(config, "config");
        this.engine =
                ThreadLocal.withInitial(() -> new FormattingEngine(this.config, fastSinglePass));
    }

    /**
     * Attempts to format the given Java source without throwing. Failures are {@link FormatResult.Failure}
     * variants with structured detail (e.g. {@link FormatResult.ParseFailure}).
     *
     * @param sourceCode the Java source to format; must not be {@code null}
     * @return success with formatted text, or a typed failure
     */
    public FormatResult formatResult(String sourceCode) {
        requireNonNull(sourceCode, SOURCE_CODE_PARAM);
        return engine.get().format(sourceCode);
    }

    /**
     * Like {@link #formatResult(String)}, but prefixes the message of any {@link FormatResult.Failure}
     * with {@code filePath}. Mirrors {@link #format(String, Path)} for callers that want the
     * non-throwing API with file-context diagnostics.
     *
     * @param sourceCode the Java source to format; must not be {@code null}
     * @param filePath path to the file, used only for diagnostics; must not be {@code null}
     * @return success with formatted text, or a path-scoped failure
     */
    public FormatResult formatResult(String sourceCode, Path filePath) {
        requireNonNull(sourceCode, SOURCE_CODE_PARAM);
        requireNonNull(filePath, FILE_PATH_PARAM);
        FormatResult result = engine.get().format(sourceCode);
        if (result instanceof FormatResult.Failure failure) {
            return new FormatResult.PathScopedFailure(filePath, failure);
        }
        return result;
    }

    /**
     * Formats the given Java source code.
     *
     * @param sourceCode the Java source to format; must not be {@code null}
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed or the pipeline cannot produce output
     */
    public String format(String sourceCode) {
        requireNonNull(sourceCode, SOURCE_CODE_PARAM);
        FormatResult result = engine.get().format(sourceCode);
        if (result instanceof FormatResult.Success success) {
            return success.formattedSource();
        }
        // FormatResult is sealed: Success | Failure — the cast is exhaustive.
        throw new FormatterException((FormatResult.Failure) result);
    }

    /**
     * Validates that {@code release} is a Java feature-release number supported by the bundled parser.
     *
     * <p>Throws {@link IllegalArgumentException} for releases the bundled JavaParser has no
     * {@code LanguageLevel} constant for. This can be used by UIs to gate input before constructing a
     * {@link io.princeofspace.model.FormatterConfig}.
     *
     * @param release Java feature-release number (e.g. {@code 17}, {@code 21})
     * @throws IllegalArgumentException if the release is not supported
     */
    public static void validateJavaRelease(int release) {
        FormattingEngine.validateJavaReleaseForParser(release);
    }

    /**
     * Formats the given Java source code, prefixing failure messages with {@code filePath}.
     *
     * @param sourceCode the Java source to format; must not be {@code null}
     * @param filePath path to the file, used only for diagnostics; must not be {@code null}
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed or the pipeline cannot produce output
     */
    public String format(String sourceCode, Path filePath) {
        requireNonNull(sourceCode, SOURCE_CODE_PARAM);
        requireNonNull(filePath, FILE_PATH_PARAM);
        FormatResult result = engine.get().format(sourceCode);
        if (result instanceof FormatResult.Success success) {
            return success.formattedSource();
        }
        // FormatResult is sealed: Success | Failure — the cast is exhaustive.
        throw new FormatterException(
                new FormatResult.PathScopedFailure(filePath, (FormatResult.Failure) result));
    }
}
