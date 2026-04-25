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
    private static final int MODERN_RELEASE_MIN = 8;
    private static final int LEGACY_RELEASE_1 = 1;
    private static final int LEGACY_RELEASE_2 = 2;
    private static final int LEGACY_RELEASE_3 = 3;
    private static final int LEGACY_RELEASE_4 = 4;
    private static final int LEGACY_RELEASE_5 = 5;
    private static final int LEGACY_RELEASE_6 = 6;
    private static final int LEGACY_RELEASE_7 = 7;

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
        if (v < MODERN_RELEASE_MIN) {
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
            case LEGACY_RELEASE_1 -> LanguageLevel.JAVA_1_0;
            case LEGACY_RELEASE_2 -> LanguageLevel.JAVA_1_1;
            case LEGACY_RELEASE_3 -> LanguageLevel.JAVA_1_2;
            case LEGACY_RELEASE_4 -> LanguageLevel.JAVA_1_4;
            case LEGACY_RELEASE_5 -> LanguageLevel.JAVA_5;
            case LEGACY_RELEASE_6 -> LanguageLevel.JAVA_6;
            case LEGACY_RELEASE_7 -> LanguageLevel.JAVA_7;
            default -> fromModernRelease(v);
        };
    }

    private static LanguageLevel fromModernRelease(int v) {
        if (v < MODERN_RELEASE_MIN) {
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
