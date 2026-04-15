package io.princeofspace.model;

import com.github.javaparser.ParserConfiguration.LanguageLevel;

import java.io.Serial;
import java.io.Serializable;

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
 *
 * @param indentStyle block indentation: spaces or tab characters per step
 * @param indentSize number of {@link IndentStyle} units per logical indent level
 * @param preferredLineLength soft target width before wrapping
 * @param maxLineLength hard cap; output must not exceed this width
 * @param continuationIndentSize {@link IndentStyle} units for each wrapped continuation line
 * @param wrapStyle how aggressively to break lines when wrapping
 * @param closingParenOnNewLine when argument lists wrap, whether the closing {@code )} is on its own line
 * @param trailingCommas whether to emit trailing commas in enums/array literals when multi-line
 * @param javaLanguageLevel language level passed to JavaParser
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
        LanguageLevel javaLanguageLevel)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates component invariants; invoked by the canonical constructor. */
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

    /**
     * Returns a configuration with all default values.
     *
     * @return default formatter configuration
     */
    public static FormatterConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a new builder initialized with default values.
     *
     * @return new builder with default values
     */
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

        /**
         * Sets spaces or tabs for block indentation.
         *
         * @param indentStyle spaces or tabs for block indentation
         * @return this builder
         */
        public Builder indentStyle(IndentStyle indentStyle) {
            this.indentStyle = indentStyle;
            return this;
        }

        /**
         * Sets the number of {@link IndentStyle} units per logical indent level.
         *
         * @param indentSize {@link IndentStyle} units per logical indent level
         * @return this builder
         */
        public Builder indentSize(int indentSize) {
            this.indentSize = indentSize;
            return this;
        }

        /**
         * Sets the soft wrap width.
         *
         * @param preferredLineLength soft wrap width
         * @return this builder
         */
        public Builder preferredLineLength(int preferredLineLength) {
            this.preferredLineLength = preferredLineLength;
            return this;
        }

        /**
         * Sets the hard maximum line width.
         *
         * @param maxLineLength hard maximum line width
         * @return this builder
         */
        public Builder maxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        /**
         * Sets the number of {@link IndentStyle} units for continuation lines.
         *
         * @param continuationIndentSize {@link IndentStyle} units for continuation lines
         * @return this builder
         */
        public Builder continuationIndentSize(int continuationIndentSize) {
            this.continuationIndentSize = continuationIndentSize;
            return this;
        }

        /**
         * Sets the line-wrapping strategy.
         *
         * @param wrapStyle line-wrapping strategy
         * @return this builder
         */
        public Builder wrapStyle(WrapStyle wrapStyle) {
            this.wrapStyle = wrapStyle;
            return this;
        }

        /**
         * Sets whether {@code )} is on its own line when lists wrap.
         *
         * @param closingParenOnNewLine whether {@code )} is on its own line when lists wrap
         * @return this builder
         */
        public Builder closingParenOnNewLine(boolean closingParenOnNewLine) {
            this.closingParenOnNewLine = closingParenOnNewLine;
            return this;
        }

        /**
         * Sets whether trailing commas are emitted in multi-line enums and array literals.
         *
         * @param trailingCommas trailing commas in enums / array literals when multi-line
         * @return this builder
         */
        public Builder trailingCommas(boolean trailingCommas) {
            this.trailingCommas = trailingCommas;
            return this;
        }

        /**
         * Sets the Java language level for the parser.
         *
         * @param javaLanguageLevel Java language level for the parser
         * @return this builder
         */
        public Builder javaLanguageLevel(LanguageLevel javaLanguageLevel) {
            this.javaLanguageLevel = javaLanguageLevel;
            return this;
        }

        /**
         * Builds a new immutable {@link FormatterConfig}.
         *
         * @return immutable formatter configuration
         */
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
