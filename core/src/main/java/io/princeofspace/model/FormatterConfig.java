package io.princeofspace.model;

import com.github.javaparser.ParserConfiguration.LanguageLevel;

/**
 * Immutable configuration for the Prince of Space formatter.
 *
 * <p>Use {@link #defaults()} to get the default configuration, or {@link #builder()} to customize
 * individual options.
 *
 * <p><b>Indent units:</b> When {@link #indentStyle()} is {@link IndentStyle#SPACES}, {@link #indentSize()}
 * and {@link #continuationIndentSize()} are counts of space characters. When it is {@link
 * IndentStyle#TABS}, both are counts of tab characters for each indent step and for each continuation,
 * respectively. See {@code docs/02-formatting-decisions.md} (sections 1 and 3).
 */
public record FormatterConfig(
        IndentStyle indentStyle,
        int indentSize,
        int preferredLineLength,
        int maxLineLength,
        int continuationIndentSize,
        WrapStyle wrapStyle,
        boolean closingParenOnNewLine,
        boolean trailingCommas,
        LanguageLevel javaLanguageLevel) {

    public FormatterConfig {
        if (indentStyle == null) throw new IllegalArgumentException("indentStyle must not be null");
        if (wrapStyle == null) throw new IllegalArgumentException("wrapStyle must not be null");
        if (javaLanguageLevel == null)
            throw new IllegalArgumentException("javaLanguageLevel must not be null");
        if (indentSize <= 0)
            throw new IllegalArgumentException("indentSize must be > 0, got: " + indentSize);
        if (continuationIndentSize <= 0)
            throw new IllegalArgumentException(
                    "continuationIndentSize must be > 0, got: " + continuationIndentSize);
        if (preferredLineLength <= 0)
            throw new IllegalArgumentException(
                    "preferredLineLength must be > 0, got: " + preferredLineLength);
        if (maxLineLength <= 0)
            throw new IllegalArgumentException("maxLineLength must be > 0, got: " + maxLineLength);
        if (maxLineLength < preferredLineLength)
            throw new IllegalArgumentException(
                    "maxLineLength (" + maxLineLength + ") must be >= preferredLineLength ("
                            + preferredLineLength + ")");
    }

    /** Returns a configuration with all default values. */
    public static FormatterConfig defaults() {
        return builder().build();
    }

    /** Returns a new builder initialized with default values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link FormatterConfig}. */
    public static final class Builder {

        private IndentStyle indentStyle = IndentStyle.SPACES;
        private int indentSize = 4;
        private int preferredLineLength = 120;
        private int maxLineLength = 150;
        private int continuationIndentSize = 4;
        private WrapStyle wrapStyle = WrapStyle.BALANCED;
        private boolean closingParenOnNewLine = true;
        private boolean trailingCommas = false;
        private LanguageLevel javaLanguageLevel = LanguageLevel.JAVA_17;

        private Builder() {}

        public Builder indentStyle(IndentStyle indentStyle) {
            this.indentStyle = indentStyle;
            return this;
        }

        public Builder indentSize(int indentSize) {
            this.indentSize = indentSize;
            return this;
        }

        public Builder preferredLineLength(int preferredLineLength) {
            this.preferredLineLength = preferredLineLength;
            return this;
        }

        public Builder maxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        public Builder continuationIndentSize(int continuationIndentSize) {
            this.continuationIndentSize = continuationIndentSize;
            return this;
        }

        public Builder wrapStyle(WrapStyle wrapStyle) {
            this.wrapStyle = wrapStyle;
            return this;
        }

        public Builder closingParenOnNewLine(boolean closingParenOnNewLine) {
            this.closingParenOnNewLine = closingParenOnNewLine;
            return this;
        }

        public Builder trailingCommas(boolean trailingCommas) {
            this.trailingCommas = trailingCommas;
            return this;
        }

        public Builder javaLanguageLevel(LanguageLevel javaLanguageLevel) {
            this.javaLanguageLevel = javaLanguageLevel;
            return this;
        }

        public FormatterConfig build() {
            return new FormatterConfig(
                    indentStyle,
                    indentSize,
                    preferredLineLength,
                    maxLineLength,
                    continuationIndentSize,
                    wrapStyle,
                    closingParenOnNewLine,
                    trailingCommas,
                    javaLanguageLevel);
        }
    }
}
