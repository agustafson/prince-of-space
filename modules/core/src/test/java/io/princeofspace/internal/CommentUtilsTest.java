package io.princeofspace.internal;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.NameExpr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
