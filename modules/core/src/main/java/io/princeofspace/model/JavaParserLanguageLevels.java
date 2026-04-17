package io.princeofspace.model;

import com.github.javaparser.ParserConfiguration.LanguageLevel;

/**
 * Translates {@link JavaLanguageLevel} (and raw integer release numbers) to JavaParser's
 * {@link LanguageLevel}.
 *
 * <p>Legacy releases 1–7 use fixed enum constants; 8+ are resolved dynamically via
 * {@link LanguageLevel#valueOf}{@code ("JAVA_N")} (or {@code "JAVA_N_PREVIEW"} for preview),
 * so new JDK levels work whenever the bundled JavaParser defines the corresponding constant.
 */
public final class JavaParserLanguageLevels {

    private JavaParserLanguageLevels() {}

    /**
     * Translates a {@link JavaLanguageLevel} to JavaParser's {@link LanguageLevel}.
     *
     * <p>For modern releases (8+), the name is built as
     * {@code "JAVA_" + level + (preview ? "_PREVIEW" : "")}.
     * For legacy releases (1–7), the preview flag is ignored and a fixed constant is returned.
     *
     * @param jll the language level to translate
     * @return matching JavaParser language level
     * @throws IllegalArgumentException if JavaParser has no matching constant
     */
    public static LanguageLevel toLanguageLevel(JavaLanguageLevel jll) {
        int v = jll.level();
        if (v < 8) {
            return fromRelease(v);
        }
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
     * @param v Java feature release number (for example, {@code 17})
     * @return matching JavaParser language level
     */
    public static LanguageLevel fromRelease(int v) {
        return switch (v) {
            case 1 -> LanguageLevel.JAVA_1_0;
            case 2 -> LanguageLevel.JAVA_1_1;
            case 3 -> LanguageLevel.JAVA_1_2;
            case 4 -> LanguageLevel.JAVA_1_4;
            case 5 -> LanguageLevel.JAVA_5;
            case 6 -> LanguageLevel.JAVA_6;
            case 7 -> LanguageLevel.JAVA_7;
            default -> fromModernRelease(v);
        };
    }

    private static LanguageLevel fromModernRelease(int v) {
        if (v < 8) {
            throw new IllegalArgumentException(
                    "Unsupported Java release " + v + " (use 1–7 for legacy levels, or 8+ for JAVA_N levels)");
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
