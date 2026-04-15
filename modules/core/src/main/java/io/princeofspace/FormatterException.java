package io.princeofspace;

/** Thrown when source code cannot be parsed or formatted. */
public final class FormatterException extends RuntimeException {

    /**
     * Creates an exception with a formatter failure message.
     *
     * @param message description of the parse or format failure
     */
    public FormatterException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a formatter failure message and cause.
     *
     * @param message description of the parse or format failure
     * @param cause underlying error (e.g. parser failure)
     */
    public FormatterException(String message, Throwable cause) {
        super(message, cause);
    }
}
