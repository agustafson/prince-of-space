package io.princeofspace.model;

import com.github.javaparser.ParserConfiguration.LanguageLevel;

/**
 * Maps a Java <strong>feature release</strong> number (for example {@code 17} for Java 17) to JavaParser's
 * {@link LanguageLevel}. Matches the CLI {@code --java-version} rules: legacy {@code 1}–{@code 7} use
 * explicit constants; {@code 8}+ use {@link LanguageLevel#valueOf}{@code ("JAVA_" + release)} so new JDK
 * levels work whenever the bundled JavaParser defines the enum constant.
 */
public final class JavaParserLanguageLevels {

    private JavaParserLanguageLevels() {}

    /** Resolve a Java feature release to JavaParser's {@link LanguageLevel}. */
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
