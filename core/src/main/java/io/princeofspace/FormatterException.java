package io.princeofspace;

/** Thrown when source code cannot be parsed or formatted. */
public final class FormatterException extends RuntimeException {

    public FormatterException(String message) {
        super(message);
    }

    public FormatterException(String message, Throwable cause) {
        super(message, cause);
    }
}
