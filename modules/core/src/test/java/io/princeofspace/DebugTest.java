package io.princeofspace;


import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual helper: formats {@code examples/inputs/java8/FormatterShowcase.java} with a fixed config and
 * writes the result to {@code /tmp/actual-wide-cont4-true.java} for diffing against goldens. Not a
 * behavioral assertion.
 */
class DebugTest {
    @Test
    void dumpFormattedShowcaseSampleToTmp() throws Exception {
        Path root = FormatterShowcaseGoldenTest.repoRoot();
        String input = Files.readString(root.resolve("examples/inputs/java8/FormatterShowcase.java"), StandardCharsets.UTF_8);
        FormatterConfig config = FormatterConfig.builder()
            .wrapStyle(WrapStyle.WIDE)
            .closingParenOnNewLine(true)
            .javaLanguageLevel(JavaLanguageLevel.of(8))
            .build();

        Formatter f = new Formatter(config);
        String result = f.format(input);
        Files.writeString(Path.of("/tmp/actual-wide-cont4-true.java"), result, StandardCharsets.UTF_8);
    }
}
