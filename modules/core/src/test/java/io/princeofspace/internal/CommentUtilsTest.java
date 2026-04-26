package io.princeofspace.internal;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class CommentUtilsTest {

    private final CommentUtils commentUtils = new CommentUtils();

    @Test
    void hasLineOrBlockComment_trueWhenNodeOwnsLineComment() {
        NameExpr expr = expr("value", 3, 10, 3, 14);
        LineComment comment = lineComment(" owner ", 3, 2, 3, 8);
        expr.setComment(comment);

        assertThat(commentUtils.hasLineOrBlockComment(expr)).isTrue();
    }

    @Test
    void hasLeadingLineOrBlockComment_trueWhenCommentIsBeforeNodeStart() {
        NameExpr expr = expr("value", 5, 12, 5, 16);
        LineComment comment = lineComment(" lead ", 5, 2, 5, 9);
        expr.setComment(comment);

        assertThat(commentUtils.hasLeadingLineOrBlockComment(expr)).isTrue();
        assertThat(commentUtils.hasTrailingLineOrBlockComment(expr)).isFalse();
    }

    @Test
    void hasTrailingLineOrBlockComment_trueWhenCommentSharesLineAfterNode() {
        NameExpr expr = expr("value", 6, 4, 6, 8);
        LineComment comment = lineComment(" trail ", 6, 12, 6, 20);
        expr.setComment(comment);

        assertThat(commentUtils.hasTrailingLineOrBlockComment(expr)).isTrue();
        assertThat(commentUtils.hasLeadingLineOrBlockComment(expr)).isFalse();
    }

    @Test
    void firstLineOrBlockCommentPrintedBeforeExpression_returnsOwnedCommentWhenBeforeExpression() {
        NameExpr expr = expr("value", 10, 8, 10, 12);
        LineComment comment = lineComment(" first ", 9, 2, 9, 9);
        expr.setComment(comment);

        assertThat(commentUtils.firstLineOrBlockCommentPrintedBeforeExpression(expr)).contains(comment);
    }

    @Test
    void hasCommentBetweenStatements_trueWhenCommentSitsBetweenStatements() {
        BlockStmt block = StaticJavaParser.parseBlock("{ int a = 1;\n // keep spacing\n int b = 2; }");
        Statement first = block.getStatement(0);
        Statement second = block.getStatement(1);

        assertThat(commentUtils.hasCommentBetweenStatements(block, first, second)).isTrue();
    }

    @Test
    void hasAnyLineOrBlockCommentOnLambda_falseForNonLambdaNode() {
        NameExpr expr = expr("value", 2, 1, 2, 5);
        expr.setComment(lineComment(" c ", 1, 1, 1, 4));

        assertThat(commentUtils.hasAnyLineOrBlockCommentOnLambda(expr)).isFalse();
    }

    @Test
    void hasAnyLineOrBlockCommentOnLambda_trueWhenLambdaOwnsLineComment() {
        LambdaExpr lambda = (LambdaExpr) StaticJavaParser.parseExpression("x -> x + 1");
        lambda.setComment(lineComment(" lambda ", 1, 1, 1, 9));

        assertThat(commentUtils.hasAnyLineOrBlockCommentOnLambda(lambda)).isTrue();
    }

    @Test
    void hasCommentBetweenNodes_trueWhenSiblingGapContainsComment() {
        ClassOrInterfaceDeclaration type =
                StaticJavaParser.parse("class T {\n  int a;\n  // spacer\n  int b;\n}\n").getClassByName("T").orElseThrow();
        var first = type.getMembers().get(0);
        var second = type.getMembers().get(1);

        assertThat(commentUtils.hasCommentBetweenNodes(first, second)).isTrue();
    }

    @Test
    void hasBlockLambdaArgument_trueWhenAtLeastOneLambdaUsesBlockBody() {
        Expression blockLambda = StaticJavaParser.parseExpression("x -> { return x; }");
        Expression inlineLambda = StaticJavaParser.parseExpression("x -> x + 1");

        assertThat(commentUtils.hasBlockLambdaArgument(new NodeList<>(inlineLambda, blockLambda))).isTrue();
        assertThat(commentUtils.hasBlockLambdaArgument(new NodeList<>(inlineLambda))).isFalse();
    }

    @Test
    void removeAllCommentsFromTree_removesOwnedAndOrphanComments() {
        MethodCallExpr expr = (MethodCallExpr) StaticJavaParser.parseExpression("foo(a)");
        expr.setComment(new BlockComment(" owned "));
        expr.addOrphanComment(new LineComment(" orphan "));

        commentUtils.removeAllCommentsFromTree(expr);

        assertThat(expr.getComment()).isEmpty();
        assertThat(expr.getOrphanComments()).isEmpty();
        assertThat(expr.getAllContainedComments()).isEmpty();
    }

    @Test
    void hoistableArgumentComment_prefersEmptyLineOrBlockCommentInMultiArgCall() {
        MethodCallExpr call = (MethodCallExpr) StaticJavaParser.parseExpression("f(a, b)");
        BlockComment emptyBlock = new BlockComment("  ");
        call.getArgument(1).setComment(emptyBlock);

        assertThat(commentUtils.hoistableArgumentComment(call)).contains(emptyBlock);
    }

    @Test
    void hoistableArgumentComment_usesDirectSingleArgumentCommentWhenPresent() {
        MethodCallExpr call = (MethodCallExpr) StaticJavaParser.parseExpression("f(value)");
        LineComment owner = lineComment(" direct ", 1, 1, 1, 9);
        call.getArgument(0).setComment(owner);

        assertThat(commentUtils.hoistableArgumentComment(call)).contains(owner);
    }

    @Test
    void extractAndDedupeLineCommentsOnTypeNameLine_keepsDistinctContentOnly() {
        ClassOrInterfaceDeclaration type =
                StaticJavaParser.parse("class Sample {\n}\n").getClassByName("Sample").orElseThrow();
        LineComment left = lineComment("dup", 1, 2, 1, 6);
        LineComment right = lineComment("dup", 1, 20, 1, 24);
        LineComment unique = lineComment("unique", 1, 30, 1, 37);
        type.addOrphanComment(left);
        type.addOrphanComment(right);
        type.addOrphanComment(unique);

        List<Comment> comments = commentUtils.extractAndDedupeLineCommentsOnTypeNameLine(type);

        assertThat(comments).hasSize(2);
        assertThat(comments).extracting(Comment::getContent).containsExactly("dup", "unique");
        assertThat(type.getOrphanComments()).isEmpty();
    }

    private static NameExpr expr(String name, int beginLine, int beginColumn, int endLine, int endColumn) {
        NameExpr expr = new NameExpr(name);
        expr.setRange(new Range(new Position(beginLine, beginColumn), new Position(endLine, endColumn)));
        return expr;
    }

    private static LineComment lineComment(
            String content, int beginLine, int beginColumn, int endLine, int endColumn) {
        LineComment comment = new LineComment(content);
        comment.setRange(new Range(new Position(beginLine, beginColumn), new Position(endLine, endColumn)));
        return comment;
    }
}
