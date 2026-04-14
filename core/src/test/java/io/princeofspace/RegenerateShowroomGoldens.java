package io.princeofspace;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Run with {@code REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens} (or
 * {@code -DregenerateShowroom=true} if your Gradle forwards system properties to the test JVM) to
 * rewrite all {@code examples/outputs/...} showroom goldens from the current formatter.
 */
class RegenerateShowroomGoldens {

    @Test
    void regenerateShowroomOutputs() throws Exception {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getProperty("regenerateShowroom"))
                        || "true".equalsIgnoreCase(System.getenv("REGENERATE_SHOWROOM")),
                "Set -DregenerateShowroom=true or REGENERATE_SHOWROOM=true to refresh golden files");

        Path root = FormatterShowcaseGoldenTest.repoRoot();
        Path inputs = root.resolve("examples/inputs");
        Path outputs = root.resolve("examples/outputs");
        LanguageLevel[] levels = {
            LanguageLevel.JAVA_8, LanguageLevel.JAVA_17, LanguageLevel.JAVA_21, LanguageLevel.JAVA_25
        };
        String[] goldens = {
            "balanced-cont4-closingparen-false.java",
            "balanced-cont4-closingparen-true.java",
            "balanced-cont8-closingparen-false.java",
            "balanced-cont8-closingparen-true.java",
            "narrow-cont4-closingparen-false.java",
            "narrow-cont4-closingparen-true.java",
            "narrow-cont8-closingparen-false.java",
            "narrow-cont8-closingparen-true.java",
            "wide-cont4-closingparen-false.java",
            "wide-cont4-closingparen-true.java",
            "wide-cont8-closingparen-false.java",
            "wide-cont8-closingparen-true.java",
        };
        for (LanguageLevel level : levels) {
            String dir = showcaseDirFor(level);
            String input = Files.readString(inputs.resolve(dir).resolve("FormatterShowcase.java"), StandardCharsets.UTF_8);
            for (String name : goldens) {
                FormatterConfig config = FormatterShowcaseGoldenTest.formatterConfigFor(level, name);
                Formatter f = new Formatter(config);
                String out = f.format(input);
                Path target = outputs.resolve(dir).resolve(name);
                Files.createDirectories(Objects.requireNonNull(target.getParent(), "golden path has parent"));
                Files.writeString(target, out, StandardCharsets.UTF_8);
            }
        }
    }

    private static String showcaseDirFor(LanguageLevel level) {
        return switch (level) {
            case JAVA_8 -> "java8";
            case JAVA_17 -> "java17";
            case JAVA_21 -> "java21";
            case JAVA_25 -> "java25";
            default -> throw new IllegalArgumentException("Unsupported showcase language level: " + level);
        };
    }
}
