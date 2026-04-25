package io.princeofspace.internal;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WidthMeasurerTest {

    private static final FormatterConfig DEFAULT_CONFIG = FormatterConfig.builder().build();

    @Test
    void flatWidth_simpleIdentifier_matchesLength() {
        Expression expr = StaticJavaParser.parseExpression("value");

        assertThat(WidthMeasurer.flatWidth(expr, DEFAULT_CONFIG)).isEqualTo(5);
    }

    @Test
    void flatWidth_methodCallChain_sumsAllSegments() {
        Expression expr = StaticJavaParser.parseExpression("items.stream().count()");

        assertThat(WidthMeasurer.flatWidth(expr, DEFAULT_CONFIG)).isEqualTo(22);
    }

    @Test
    void flatWidth_blockLambda_treatsBodyAsBraced() {
        Expression expr = StaticJavaParser.parseExpression("(x) -> { x++; }");

        assertThat(WidthMeasurer.flatWidth(expr, DEFAULT_CONFIG)).isEqualTo(10);
    }

    @Test
    void flatWidth_stringLiteral_includesQuotes() {
        Expression expr = StaticJavaParser.parseExpression("\"hello\"");

        assertThat(WidthMeasurer.flatWidth(expr, DEFAULT_CONFIG)).isEqualTo(7);
    }

    @Test
    void flatWidth_binaryExpr_includesOperatorSpacing() {
        Expression expr = StaticJavaParser.parseExpression("left + right");

        assertThat(WidthMeasurer.flatWidth(expr, DEFAULT_CONFIG)).isEqualTo(12);
    }
}
