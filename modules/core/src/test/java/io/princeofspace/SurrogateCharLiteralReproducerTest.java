package io.princeofspace;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SurrogateCharLiteralReproducerTest {

    @Test
    void surrogateCharValuesSurviveSavingAsUtf8() {
        String input = "class Surrogates { char high = '\\uD801'; char low = '\\uDC01'; }";
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .indentSize(2)
                .lineLength(100)
                .javaLanguageLevel(JavaLanguageLevel.of(25))
                .build());

        String output = formatter.format(input);
        String persisted = new String(output.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                .as("UTF-16 code units after a UTF-8 round trip")
                .extracting(literal -> (int) literal.asChar())
                .containsExactly(0xD801, 0xDC01);
    }

    @Test
    void surrogateBoundariesAndOrdinaryCharsSurviveSavingAsUtf8() {
        String input = "class Chars { char[] values = {'\\uD800', '\\uDBFF', '\\uDC00', '\\uDFFF', "
                + "'a', '\\n', '\\'', '\\\\', '\\141'}; }";
        Formatter formatter = new Formatter(FormatterConfig.builder().lineLength(100).build());

        String output = formatter.format(input);
        String persisted = new String(output.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                .extracting(literal -> (int) literal.asChar())
                .containsExactly(0xD800, 0xDBFF, 0xDC00, 0xDFFF, 97, 10, 39, 92, 97);
    }

    @Test
    void escapedSurrogateWidthsAndCommentsArePreserved() {
        String input = "class Chars { void test() { consume('\\uD800', /* middle */ '\\uDC00', "
                + "'\\uDBFF', '\\uDFFF'); } }";
        Formatter formatter = new Formatter(FormatterConfig.builder().lineLength(50).build());

        String output = formatter.format(input);

        assertThat(output).contains("/* middle */");
        assertThat(output.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(50));
        assertThat(formatter.format(output)).isEqualTo(output);
        String persisted = new String(output.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(StaticJavaParser.parse(persisted).findAll(CharLiteralExpr.class))
                .extracting(literal -> (int) literal.asChar())
                .containsExactly(0xD800, 0xDC00, 0xDBFF, 0xDFFF);
    }
}
