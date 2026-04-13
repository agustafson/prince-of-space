package io.princeofspace;

import io.princeofspace.internal.FormattingEngine;
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
 */
public final class Formatter {

    private final FormattingEngine engine;

    /**
     * @param config formatting options; must not be {@code null}
     */
    public Formatter(FormatterConfig config) {
        this.engine = new FormattingEngine(requireNonNull(config, "config"));
    }

    /**
     * Formats the given Java source code.
     *
     * @param sourceCode the Java source to format
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed
     */
    public String format(String sourceCode) {
        return engine.format(sourceCode);
    }

    /**
     * Formats the given Java source code, attaching {@code filePath} to any error messages.
     *
     * @param sourceCode the Java source to format
     * @param filePath path to the file, used only for diagnostics
     * @return formatted source code
     * @throws FormatterException if the source cannot be parsed
     */
    public String format(String sourceCode, Path filePath) {
        try {
            return engine.format(sourceCode);
        } catch (FormatterException e) {
            throw new FormatterException(filePath + ": " + e.getMessage(), e);
        }
    }
}
