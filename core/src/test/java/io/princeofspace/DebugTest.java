package io.princeofspace;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class DebugTest {
    @Test
    void debug() throws Exception {
        Path root = FormatterShowcaseGoldenTest.repoRoot();
        String input = Files.readString(root.resolve("examples/inputs/java8/FormatterShowcase.java"), StandardCharsets.UTF_8);
        FormatterConfig config = FormatterConfig.builder()
            .wrapStyle(WrapStyle.WIDE)
            .continuationIndentSize(4)
            .closingParenOnNewLine(true)
            .javaLanguageLevel(LanguageLevel.JAVA_8)
            .build();

        Formatter f = new Formatter(config);
        String result = f.format(input);
        Files.writeString(Path.of("/tmp/actual-wide-cont4-true.java"), result, StandardCharsets.UTF_8);
    }
}
