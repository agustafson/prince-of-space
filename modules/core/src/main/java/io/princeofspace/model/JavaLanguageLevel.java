package io.princeofspace.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Java language level for the formatter's parser, expressed as a feature-release integer and an
 * optional preview flag.
 *
 * <p>Use {@link #of(int)} for standard releases (e.g. {@code JavaLanguageLevel.of(21)}) or
 * {@link #of(int, boolean)} for preview releases (e.g. {@code JavaLanguageLevel.of(21, true)}).
 *
 * <p>Translated to a JavaParser {@code LanguageLevel} internally via
 * {@link JavaParserLanguageLevels#toLanguageLevel(JavaLanguageLevel)}.
 *
 * @param level   Java feature-release number (e.g. {@code 17} for Java 17, {@code 1}–{@code 7} for legacy)
 * @param preview whether to enable JavaParser's preview-language-feature parsing
 */
public record JavaLanguageLevel(int level, boolean preview) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates that {@code level} is a positive integer. */
    public JavaLanguageLevel {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1, got: " + level);
        }
    }

    /**
     * Returns a standard (non-preview) language level for the given feature release.
     *
     * @param level Java feature-release number (e.g. {@code 17})
     * @return standard language level
     */
    public static JavaLanguageLevel of(int level) {
        return new JavaLanguageLevel(level, false);
    }

    /**
     * Returns a language level for the given feature release with explicit preview control.
     *
     * @param level   Java feature-release number (e.g. {@code 21})
     * @param preview {@code true} to enable preview language features
     * @return language level with the requested preview setting
     */
    public static JavaLanguageLevel of(int level, boolean preview) {
        return new JavaLanguageLevel(level, preview);
    }
}
