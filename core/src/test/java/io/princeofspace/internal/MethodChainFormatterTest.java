package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodChainFormatterTest {

    private final MethodChainFormatter formatter = new MethodChainFormatter(null, new CommentUtils());

    @Test
    void shouldGlueWrappedClosingParen_trueForSingleMethodCallArgument() {
        NodeList<Expression> args = new NodeList<>(new MethodCallExpr(new NameExpr("x"), "call"));

        assertThat(formatter.shouldGlueWrappedClosingParen(args)).isTrue();
    }

    @Test
    void shouldGlueWrappedClosingParen_trueForBlockLambdaArgument() {
        LambdaExpr lambda = new LambdaExpr(new NodeList<>(), new BlockStmt());
        NodeList<Expression> args = new NodeList<>(lambda);

        assertThat(formatter.shouldGlueWrappedClosingParen(args)).isTrue();
    }

    @Test
    void shouldGlueWrappedClosingParen_falseForSimpleNonLambdaArguments() {
        NodeList<Expression> args = new NodeList<>(new NameExpr("a"), new NameExpr("b"));

        assertThat(formatter.shouldGlueWrappedClosingParen(args)).isFalse();
    }
}
