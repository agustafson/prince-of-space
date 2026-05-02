package io.princeofspace.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable configuration for the Prince of Space formatter.
 *
 * <p>Use {@link #defaults()} to get the default configuration, or {@link #builder()} to customize
 * individual options.
 *
 * <p><b>Indent units:</b> When {@link #indentStyle()} is {@link IndentStyle#SPACES}, {@link #indentSize()}
 * is a count of space characters. When it is {@link IndentStyle#TABS}, it is a count of tab characters
 * per indent step. See {@code docs/formatting-rules.md} (sections 1 and 3).
 *
 * <p><b>Continuation indent:</b> Wrapped continuation lines are always indented by {@code 2 * indentSize}
 * units (see {@link #continuationIndentSize()}). This is not configurable — it follows the
 * Oracle/IntelliJ convention and ensures parameters are visually distinct from the method body.
 *
 * <p><b>Default option values:</b> {@link #DEFAULT_INDENT_STYLE}, {@link #DEFAULT_INDENT_SIZE},
 * {@link #DEFAULT_LINE_LENGTH}, {@link #DEFAULT_WRAP_STYLE}, {@link #DEFAULT_CLOSING_PAREN_ON_NEW_LINE},
 * {@link #DEFAULT_TRAILING_COMMAS}, and {@link #DEFAULT_JAVA_LANGUAGE_LEVEL} are the single source of truth
 * for {@link #defaults()} and {@link Builder}; README and architecture docs reference the same names.
 *
 * @param indentStyle block indentation: spaces or tab characters per step
 * @param indentSize number of {@link IndentStyle} units per logical indent level
 * @param lineLength target line width; wrapping is triggered when a line exceeds this
 * @param wrapStyle how aggressively to break lines when wrapping
 * @param closingParenOnNewLine when argument lists wrap, whether the closing {@code )} is on its own line
 * @param trailingCommas whether to emit trailing commas in enums/array literals when multi-line
 * @param javaLanguageLevel language level passed to JavaParser
 */
public record FormatterConfig(
        IndentStyle indentStyle,
        int indentSize,
        int lineLength,
        WrapStyle wrapStyle,
        boolean closingParenOnNewLine,
        boolean trailingCommas,
        JavaLanguageLevel javaLanguageLevel)
        implements Serializable {

    /** Default {@link IndentStyle} for {@link #defaults()} and {@link Builder}. */
    public static final IndentStyle DEFAULT_INDENT_STYLE = IndentStyle.SPACES;

    /** Default indent step size ({@link IndentStyle} units per block level) for {@link #defaults()} and {@link Builder}. */
    public static final int DEFAULT_INDENT_SIZE = 4;

    /** Default {@link #lineLength()} for {@link #defaults()} and {@link Builder}. */
    public static final int DEFAULT_LINE_LENGTH = 120;

    /** Default {@link WrapStyle} for {@link #defaults()} and {@link Builder}. */
    public static final WrapStyle DEFAULT_WRAP_STYLE = WrapStyle.BALANCED;

    /** Default {@link #closingParenOnNewLine()} for {@link #defaults()} and {@link Builder}. */
    public static final boolean DEFAULT_CLOSING_PAREN_ON_NEW_LINE = true;

    /** Default {@link #trailingCommas()} for {@link #defaults()} and {@link Builder}. */
    public static final boolean DEFAULT_TRAILING_COMMAS = false;

    /** Default {@link #javaLanguageLevel()} for {@link #defaults()} and {@link Builder}. */
    public static final JavaLanguageLevel DEFAULT_JAVA_LANGUAGE_LEVEL = JavaLanguageLevel.of(17);

    /**
     * Default Java feature-release number for {@code --java-version}-style tooling (same value as
     * {@link #DEFAULT_JAVA_LANGUAGE_LEVEL}{@code .level()}).
     *
     * @return the default feature-release number (e.g. {@code 17})
     */
    public static int defaultJavaVersion() {
        return DEFAULT_JAVA_LANGUAGE_LEVEL.level();
    }

    /**
     * Line separator passed to JavaParser's printer ({@code END_OF_LINE_CHARACTER}) and text-block delimiters.
     * Not exposed as a user knob: deterministic LF output is required for CI goldens and cross-platform checks.
     */
    public static final String DEFAULT_PRINTER_LINE_SEPARATOR = "\n";

    /**
     * JavaParser {@code SPACE_AROUND_OPERATORS} setting; fixed to match the formatter's baseline operator spacing.
     */
    public static final boolean DEFAULT_PRINTER_SPACE_AROUND_OPERATORS = true;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates component invariants; invoked by the canonical constructor.
     *
     * <p>Null components throw {@link NullPointerException} (the JDK convention for missing
     * required arguments). Non-null but out-of-range values throw {@link IllegalArgumentException}.
     */
    public FormatterConfig {
        Objects.requireNonNull(indentStyle, "indentStyle");
        Objects.requireNonNull(wrapStyle, "wrapStyle");
        Objects.requireNonNull(javaLanguageLevel, "javaLanguageLevel");
        if (indentSize <= 0)
            throw new IllegalArgumentException("indentSize must be > 0, got: " + indentSize);
        if (lineLength <= 0)
            throw new IllegalArgumentException("lineLength must be > 0, got: " + lineLength);
    }

    /**
     * Returns the continuation indent size, always {@code 2 * indentSize}.
     *
     * <p>This follows the Oracle/IntelliJ convention (indent=4 → continuation=8, indent=2 →
     * continuation=4) and guarantees that wrapped parameters are visually distinct from the method
     * body at any indent size.
     *
     * @return continuation indent size in {@link IndentStyle} units
     */
    public int continuationIndentSize() {
        return indentSize * 2;
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
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    public static final class Builder {

        private IndentStyle indentStyle = DEFAULT_INDENT_STYLE;
        private int indentSize = DEFAULT_INDENT_SIZE;
        private int lineLength = DEFAULT_LINE_LENGTH;
        private WrapStyle wrapStyle = DEFAULT_WRAP_STYLE;
        private boolean closingParenOnNewLine = DEFAULT_CLOSING_PAREN_ON_NEW_LINE;
        private boolean trailingCommas = DEFAULT_TRAILING_COMMAS;
        private JavaLanguageLevel javaLanguageLevel = DEFAULT_JAVA_LANGUAGE_LEVEL;

        private Builder() {}

        /**
         * Sets spaces or tabs for block indentation.
         *
         * @param indentStyle spaces or tabs for block indentation
         * @return this builder
         */
        public Builder indentStyle(IndentStyle indentStyle) {
            this.indentStyle = Objects.requireNonNull(indentStyle, "indentStyle");
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
         * Sets the line-wrapping strategy.
         *
         * @param wrapStyle line-wrapping strategy
         * @return this builder
         */
        public Builder wrapStyle(WrapStyle wrapStyle) {
            this.wrapStyle = Objects.requireNonNull(wrapStyle, "wrapStyle");
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
            this.javaLanguageLevel = Objects.requireNonNull(javaLanguageLevel, "javaLanguageLevel");
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
                    wrapStyle,
                    closingParenOnNewLine,
                    trailingCommas,
                    javaLanguageLevel);
        }
    }
}
