package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
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
        JavaLanguageLevel[] levels = {
            JavaLanguageLevel.of(8), JavaLanguageLevel.of(17),
            JavaLanguageLevel.of(21), JavaLanguageLevel.of(25)
        };
        String[] goldens = {
            "balanced-closingparen-false.java",
            "balanced-closingparen-true.java",
            "narrow-closingparen-false.java",
            "narrow-closingparen-true.java",
            "wide-closingparen-false.java",
            "wide-closingparen-true.java",
        };
        for (JavaLanguageLevel level : levels) {
            String dir = FormatterShowcaseGoldenTest.showcaseDirFor(level);
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
}
