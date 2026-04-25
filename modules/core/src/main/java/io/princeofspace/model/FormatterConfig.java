package io.princeofspace.model;

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
 * respectively. See {@code docs/formatting-rules.md} (sections 1 and 3).
 *
 * @param indentStyle block indentation: spaces or tab characters per step
 * @param indentSize number of {@link IndentStyle} units per logical indent level
 * @param lineLength target line width; wrapping is triggered when a line exceeds this
 * @param continuationIndentSize {@link IndentStyle} units for each wrapped continuation line
 * @param wrapStyle how aggressively to break lines when wrapping
 * @param closingParenOnNewLine when argument lists wrap, whether the closing {@code )} is on its own line
 * @param trailingCommas whether to emit trailing commas in enums/array literals when multi-line
 * @param javaLanguageLevel language level passed to JavaParser
 */
public record FormatterConfig(
        IndentStyle indentStyle,
        int indentSize,
        int lineLength,
        int continuationIndentSize,
        WrapStyle wrapStyle,
        boolean closingParenOnNewLine,
        boolean trailingCommas,
        JavaLanguageLevel javaLanguageLevel)
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
        if (lineLength <= 0)
            throw new IllegalArgumentException("lineLength must be > 0, got: " + lineLength);
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
        private int lineLength = 120;
        private int continuationIndentSize = 4;
        private WrapStyle wrapStyle = WrapStyle.BALANCED;
        private boolean closingParenOnNewLine = true;
        private boolean trailingCommas = false;
        private JavaLanguageLevel javaLanguageLevel = JavaLanguageLevel.of(17);

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
         * Sets the target line width.
         *
         * @param lineLength target line width
         * @return this builder
         */
        public Builder lineLength(int lineLength) {
            this.lineLength = lineLength;
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
        public Builder javaLanguageLevel(JavaLanguageLevel javaLanguageLevel) {
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
                    lineLength,
                    continuationIndentSize,
                    wrapStyle,
                    closingParenOnNewLine,
                    trailingCommas,
                    javaLanguageLevel);
        }
    }
}
