package io.princeofspace;

import io.princeofspace.model.FormatResult;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Thrown when source code cannot be parsed or formatted. */
public final class FormatterException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Populated only when constructed via {@link #FormatterException(FormatResult.Failure)}.
     *
     * <p>{@link Optional} is not {@link java.io.Serializable}; if this exception is serialized, the payload is
     * dropped — acceptable because structured failures are for in-process handling (CLI/IDE), not RMI.
     */
    @SuppressWarnings("PMD.NonSerializableClass")
    private final Optional<FormatResult.Failure> capturedFailure;

    /**
     * Creates an exception with a formatter failure message.
     *
     * @param message description of the parse or format failure
     */
    public FormatterException(String message) {
        super(message);
        this.capturedFailure = Optional.empty();
    }

    /**
     * Creates an exception from a structured {@link FormatResult.Failure} (for example
     * {@link FormatResult.ParseFailure} or {@link FormatResult.NonConvergent}).
     *
     * @param failure the formatter failure; message is taken from {@link FormatResult.Failure#message()}
     */
    public FormatterException(FormatResult.Failure failure) {
        super(failure.message());
        this.capturedFailure = Optional.of(failure);
    }

    /**
     * Creates an exception with a formatter failure message and cause.
     *
     * @param message description of the parse or format failure
     * @param cause underlying error (e.g. parser failure)
     */
    public FormatterException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.capturedFailure = Optional.empty();
    }

    /**
     * When this exception was created via {@link #FormatterException(FormatResult.Failure)}, returns that
     * failure for typed handling; otherwise empty.
     *
     * @return structured failure when thrown from {@link #FormatterException(FormatResult.Failure)}
     */
    public Optional<FormatResult.Failure> formatFailure() {
        return capturedFailure;
    }

    /**
     * {@code true} when failure is (or wraps) {@link FormatResult.NonConvergent} — formatting did not reach a
     * fixed point. That usually indicates a formatter defect; callers may surface it distinctly from parse
     * errors (for example the CLI uses exit code {@code 3}).
     *
     * @return whether the failure chain contains {@link FormatResult.NonConvergent}
     */
    public boolean isNonConvergent() {
        return capturedFailure.map(FormatterException::failureTreeIsNonConvergent).orElse(false);
    }

    private static boolean failureTreeIsNonConvergent(FormatResult.Failure failure) {
        if (failure instanceof FormatResult.NonConvergent) {
            return true;
        }
        if (failure instanceof FormatResult.PathScopedFailure pathScoped) {
            return failureTreeIsNonConvergent(pathScoped.cause());
        }
        return false;
    }
}
