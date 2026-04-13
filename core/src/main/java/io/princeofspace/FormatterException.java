package io.princeofspace;

/** Thrown when source code cannot be parsed or formatted. */
public final class FormatterException extends RuntimeException {

    /** @param message description of the parse or format failure */
    public FormatterException(String message) {
        super(message);
    }

    /**
     * @param message description of the parse or format failure
     * @param cause underlying error (e.g. parser failure)
     */
    public FormatterException(String message, Throwable cause) {
        super(message, cause);
    }
}
