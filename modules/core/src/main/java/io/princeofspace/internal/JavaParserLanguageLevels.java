package io.princeofspace.internal;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.JavaLanguageLevel;

/**
 * Translates {@link JavaLanguageLevel} (and raw integer release numbers) to JavaParser's
 * {@link LanguageLevel}.
 *
 * <p>Releases 8+ are resolved dynamically via
 * {@link LanguageLevel#valueOf}{@code ("JAVA_N")} (or {@code "JAVA_N_PREVIEW"} for preview),
 * so new JDK levels work whenever the bundled JavaParser defines the corresponding constant.
 */
final class JavaParserLanguageLevels {
    static final int MIN_SUPPORTED_RELEASE = 8;

    private JavaParserLanguageLevels() {}

    /**
     * Translates a {@link JavaLanguageLevel} to JavaParser's {@link LanguageLevel}.
     *
     * <p>The name is built as {@code "JAVA_" + level + (preview ? "_PREVIEW" : "")}.
     *
     * @param jll the language level to translate
     * @return matching JavaParser language level
     * @throws IllegalArgumentException if JavaParser has no matching constant
     */
    static LanguageLevel toLanguageLevel(JavaLanguageLevel jll) {
        int v = jll.level();
        String name = "JAVA_" + v + (jll.preview() ? "_PREVIEW" : "");
        try {
            return LanguageLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported Java release "
                            + v
                            + (jll.preview() ? " (preview)" : "")
                            + ": JavaParser has no LanguageLevel."
                            + name
                            + " (upgrade the javaparser dependency, or pick a level your build supports).",
                    e);
        }
    }

    /**
     * Resolves a raw Java feature-release number to JavaParser's {@link LanguageLevel}.
     *
     * @param v Java feature release number (for example, {@code 17}); must be {@link #MIN_SUPPORTED_RELEASE} or greater
     * @return matching JavaParser language level
     * @throws IllegalArgumentException if the release is below 8 or JavaParser has no matching constant
     */
    static LanguageLevel fromRelease(int v) {
        if (v < MIN_SUPPORTED_RELEASE) {
            throw new IllegalArgumentException(
                    "Unsupported Java release " + v + " (minimum supported release is " + MIN_SUPPORTED_RELEASE + ")");
        }
        String name = "JAVA_" + v;
        try {
            return LanguageLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported Java release "
                            + v
                            + ": JavaParser has no LanguageLevel."
                            + name
                            + " (upgrade the javaparser dependency, or pick a level your build supports).",
                    e);
        }
    }
}
