package io.princeofspace.internal;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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

    @Test
    void hasLineOrBlockComment_trueWhenNodeOwnsLineComment() {
        NameExpr expr = expr("value", 3, 10, 3, 14);
        LineComment comment = lineComment(" owner ", 3, 2, 3, 8);
        expr.setComment(comment);

        assertThat(CommentUtils.hasLineOrBlockComment(expr)).isTrue();
    }

    @Test
    void hasLeadingLineOrBlockComment_trueWhenCommentIsBeforeNodeStart() {
        NameExpr expr = expr("value", 5, 12, 5, 16);
        LineComment comment = lineComment(" lead ", 5, 2, 5, 9);
        expr.setComment(comment);

        assertThat(CommentUtils.hasLeadingLineOrBlockComment(expr)).isTrue();
        assertThat(CommentUtils.hasTrailingLineOrBlockComment(expr)).isFalse();
    }

    @Test
    void hasTrailingLineOrBlockComment_trueWhenCommentSharesLineAfterNode() {
        NameExpr expr = expr("value", 6, 4, 6, 8);
        LineComment comment = lineComment(" trail ", 6, 12, 6, 20);
        expr.setComment(comment);

        assertThat(CommentUtils.hasTrailingLineOrBlockComment(expr)).isTrue();
        assertThat(CommentUtils.hasLeadingLineOrBlockComment(expr)).isFalse();
    }

    @Test
    void firstLineOrBlockCommentPrintedBeforeExpression_returnsOwnedCommentWhenBeforeExpression() {
        NameExpr expr = expr("value", 10, 8, 10, 12);
        LineComment comment = lineComment(" first ", 9, 2, 9, 9);
        expr.setComment(comment);

        assertThat(CommentUtils.firstLineOrBlockCommentPrintedBeforeExpression(expr)).contains(comment);
    }

    @Test
    void hasCommentBetweenStatements_trueWhenCommentSitsBetweenStatements() {
        BlockStmt block = StaticJavaParser.parseBlock("{ int a = 1;\n // keep spacing\n int b = 2; }");
        Statement first = block.getStatement(0);
        Statement second = block.getStatement(1);

        assertThat(CommentUtils.hasCommentBetweenStatements(block, first, second)).isTrue();
    }

    @Test
    void hasAnyLineOrBlockCommentOnLambda_falseForNonLambdaNode() {
        NameExpr expr = expr("value", 2, 1, 2, 5);
        expr.setComment(lineComment(" c ", 1, 1, 1, 4));

        assertThat(CommentUtils.hasAnyLineOrBlockCommentOnLambda(expr)).isFalse();
    }

    @Test
    void hasAnyLineOrBlockCommentOnLambda_trueWhenLambdaOwnsLineComment() {
        LambdaExpr lambda = (LambdaExpr) StaticJavaParser.parseExpression("x -> x + 1");
        lambda.setComment(lineComment(" lambda ", 1, 1, 1, 9));

        assertThat(CommentUtils.hasAnyLineOrBlockCommentOnLambda(lambda)).isTrue();
    }

    @Test
    void hasCommentBetweenNodes_trueWhenSiblingGapContainsComment() {
        ClassOrInterfaceDeclaration type =
                StaticJavaParser.parse("class T {\n  int a;\n  // spacer\n  int b;\n}\n").getClassByName("T").orElseThrow();
        var first = type.getMembers().get(0);
        var second = type.getMembers().get(1);

        assertThat(CommentUtils.hasCommentBetweenNodes(first, second)).isTrue();
    }

    @Test
    void hasBlockLambdaArgument_trueWhenAtLeastOneLambdaUsesBlockBody() {
        Expression blockLambda = StaticJavaParser.parseExpression("x -> { return x; }");
        Expression inlineLambda = StaticJavaParser.parseExpression("x -> x + 1");

        assertThat(CommentUtils.hasBlockLambdaArgument(new NodeList<>(inlineLambda, blockLambda))).isTrue();
        assertThat(CommentUtils.hasBlockLambdaArgument(new NodeList<>(inlineLambda))).isFalse();
    }

    @Test
    void removeMirroredCommentOwners_keepsNestedCommentsAndParents() {
        MethodCallExpr expr = (MethodCallExpr) StaticJavaParser.parseExpression("foo(a)");
        BlockComment leading = new BlockComment(" repeated ");
        BlockComment nested = new BlockComment(" repeated ");
        expr.setComment(leading);
        expr.addOrphanComment(new LineComment(" orphan "));
        expr.getArgument(0).setComment(nested);

        CommentUtils.removeMirroredCommentOwners(expr, leading);

        assertThat(expr.getComment()).isEmpty();
        assertThat(expr.getOrphanComments()).hasSize(1);
        assertThat(expr.getArgument(0).getComment()).containsSame(nested);
        assertThat(expr.getArgument(0).getParentNode()).containsSame(expr);
    }

    @Test
    void sourceCommentIndexRetainsReparentedCommentPositions() {
        var unit = StaticJavaParser.parse("class T { void f() { call(1, /* repeated */ 2, /* repeated */ 3); } }");
        MethodCallExpr call = unit.findFirst(MethodCallExpr.class).orElseThrow();
        List<Comment> comments = unit.getAllContainedComments();
        assertThat(comments).hasSize(2);
        CommentUtils.indexSourceCommentStarts(unit);
        for (Comment comment : comments) {
            CommentUtils.removeMirroredCommentOwners(unit, comment);
            call.addOrphanComment(comment);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < comments.size(); i++) {
                Comment comment = comments.get(i);
                assertThat(CommentUtils.takeExpressionBoundaryComments(call.getArgument(i + 1)))
                        .singleElement().isSameAs(comment);
                call.addOrphanComment(comment);
            }
        }
    }

    @Test
    void sourceCommentIndexHandlesCommentFreeUnits() {
        var unit = StaticJavaParser.parse("class T { void f() { call(1, 2, 3); } }");
        String original = unit.toString();
        CommentUtils.indexSourceCommentStarts(unit);
        CommentUtils.normaliseCallCommentOwners(unit);
        assertThat(unit.toString()).isEqualTo(original);
        assertThat(unit.getAllContainedComments()).isEmpty();
    }

    @Test
    void sourceCommentIndexKeepsStrictGapEndpoints() {
        var unit = StaticJavaParser.parse("class T { void f() { call(1, /* start */ /* inside */ /* end */ 2); } }");
        MethodCallExpr call = unit.findFirst(MethodCallExpr.class).orElseThrow();
        List<Comment> comments = unit.getAllContainedComments();
        assertThat(comments).hasSize(3);
        comments.get(0).setRange(new Range(call.getArgument(0).getEnd().orElseThrow(),
                comments.get(0).getEnd().orElseThrow()));
        comments.get(2).setRange(new Range(comments.get(2).getBegin().orElseThrow(),
                call.getArgument(1).getBegin().orElseThrow()));
        CommentUtils.indexSourceCommentStarts(unit);
        for (Comment comment : comments) {
            CommentUtils.removeMirroredCommentOwners(unit, comment);
            call.addOrphanComment(comment);
        }
        assertThat(CommentUtils.takeExpressionBoundaryComments(call.getArgument(1)))
                .singleElement().isSameAs(comments.get(1));
    }

    @Test
    void commentGapLookupSupportsDetachedAndProgrammaticRoots() {
        MethodCallExpr call = (MethodCallExpr) StaticJavaParser.parseExpression("call(1, /* label */ 2)");
        Comment comment = call.getAllContainedComments().get(0);
        CommentUtils.removeMirroredCommentOwners(call, comment);
        call.addOrphanComment(comment);
        assertThat(CommentUtils.takeExpressionBoundaryComments(call.getArgument(1)))
                .singleElement().isSameAs(comment);

        call.addOrphanComment(comment);
        CompilationUnit unit = new CompilationUnit();
        unit.addClass("T").addMethod("f").setBody(new BlockStmt().addStatement(call));
        CommentUtils.indexSourceCommentStarts(unit);
        assertThat(CommentUtils.takeExpressionBoundaryComments(call.getArgument(1)))
                .singleElement().isSameAs(comment);
    }

    @Test
    void hoistableArgumentComment_keepsCommentsInsideMultiArgCall() {
        MethodCallExpr call = (MethodCallExpr) StaticJavaParser.parseExpression("f(a, b)");
        BlockComment emptyBlock = new BlockComment("  ");
        call.getArgument(1).setComment(emptyBlock);

        assertThat(CommentUtils.hoistableArgumentComment(call)).isEmpty();
    }

    @Test
    void hoistableArgumentComment_usesDirectSingleArgumentCommentWhenPresent() {
        MethodCallExpr call = (MethodCallExpr) StaticJavaParser.parseExpression("f(value)");
        LineComment owner = lineComment(" direct ", 1, 1, 1, 9);
        call.getArgument(0).setComment(owner);

        assertThat(CommentUtils.hoistableArgumentComment(call)).contains(owner);
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

        List<Comment> comments = CommentUtils.extractAndDedupeLineCommentsOnTypeNameLine(type);

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
