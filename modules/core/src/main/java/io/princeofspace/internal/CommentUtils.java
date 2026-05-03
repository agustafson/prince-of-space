package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Static comment-oriented AST helpers used by visitor delegates. */
final class CommentUtils {

    private static final int SINGLE_ARGUMENT_COUNT = 1;

    private CommentUtils() {}

    /** Returns whether comments exist between two statements in the same block. */
    static boolean hasCommentBetweenStatements(BlockStmt block, Statement previous, Statement current) {
        return hasCommentBetweenStatements(block.getAllContainedComments(), previous, current);
    }

    /**
     * Same predicate as {@link #hasCommentBetweenStatements(BlockStmt, Statement, Statement)} but
     * receives a pre-collected list of the block's contained comments so the caller can amortize
     * one {@link Node#getAllContainedComments()} traversal across all statement pairs in the block
     * (the underlying call recursively walks the whole subtree and allocates a fresh
     * {@link java.util.LinkedList}, so calling it once per pair is O(N²) per block).
     */
    static boolean hasCommentBetweenStatements(
            List<Comment> blockContainedComments, Statement previous, Statement current) {
        if (blockContainedComments.isEmpty()
                || previous.getRange().isEmpty()
                || current.getRange().isEmpty()) {
            return false;
        }
        int startLineExclusive = previous.getRange().get().end.line;
        int currentLine = current.getRange().get().begin.line;
        int currentColumn = current.getRange().get().begin.column;
        for (Comment comment : blockContainedComments) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            int line = comment.getRange().get().begin.line;
            if (line > startLineExclusive && line < currentLine) {
                return true;
            }
            if (line == currentLine && comment.getRange().get().end.column < currentColumn) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when a line/block comment would print before the node body. */
    static boolean hasLineOrBlockCommentPrintedBeforeNode(Node node) {
        if (node.getRange().isEmpty()) {
            return false;
        }
        int nodeBegin = node.getRange().get().begin.line;
        Optional<Comment> c = node.getComment();
        if (c.isPresent()
                && (c.get() instanceof LineComment || c.get() instanceof BlockComment)
                && c.get().getRange().isPresent()
                && c.get().getRange().get().begin.line <= nodeBegin) {
            return true;
        }
        for (Comment nested : node.getAllContainedComments()) {
            if ((nested instanceof LineComment || nested instanceof BlockComment)
                    && nested.getRange().isPresent()
                    && nested.getRange().get().begin.line <= nodeBegin) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when a lambda node owns a line/block comment. */
    static boolean hasAnyLineOrBlockCommentOnLambda(Node node) {
        if (!(node instanceof LambdaExpr)) {
            return false;
        }
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    /** Returns true when a node's direct comment is line/block style. */
    static boolean hasLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    /** Returns true for line/block comments that contain only whitespace. */
    static boolean isEmptyLineOrBlockComment(Comment comment) {
        return (comment instanceof LineComment || comment instanceof BlockComment)
                && comment.getContent().trim().isEmpty();
    }

    /** Returns first line/block comment that would print before an expression. */
    static Optional<Comment> firstLineOrBlockCommentPrintedBeforeExpression(Expression expression) {
        if (expression.getRange().isEmpty()) {
            return Optional.empty();
        }
        int expressionBeginLine = expression.getRange().get().begin.line;
        int expressionBeginColumn = expression.getRange().get().begin.column;
        List<Comment> comments = new ArrayList<>();
        expression.getComment().ifPresent(comments::add);
        comments.addAll(expression.getAllContainedComments());
        return comments.stream()
                .filter(comment -> isCommentBeforeExpression(comment, expressionBeginLine, expressionBeginColumn))
                .min(
                        Comparator.comparingInt(
                                        (Comment comment) ->
                                                comment.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE))
                                .thenComparingInt(
                                        comment ->
                                                comment.getRange().map(r -> r.begin.column).orElse(Integer.MAX_VALUE)));
    }

    /** Returns true when comment starts before expression source position. */
    private static boolean isCommentBeforeExpression(Comment comment, int expressionBeginLine, int expressionBeginColumn) {
        if (!isLineOrBlock(comment) || comment.getRange().isEmpty()) {
            return false;
        }
        int commentBeginLine = comment.getRange().get().begin.line;
        if (commentBeginLine < expressionBeginLine) {
            return true;
        }
        if (commentBeginLine > expressionBeginLine) {
            return false;
        }
        return comment.getRange().get().end.column < expressionBeginColumn;
    }

    /** Returns true when node or descendants contain a line/block comment. */
    static boolean hasAnyLineOrBlockComment(Node node) {
        if (node.getComment().isPresent()
                && (node.getComment().get() instanceof LineComment
                        || node.getComment().get() instanceof BlockComment)) {
            return true;
        }
        for (Comment c : node.getAllContainedComments()) {
            if (c instanceof LineComment || c instanceof BlockComment) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when there is a comment between two sibling nodes by range. */
    static boolean hasCommentBetweenNodes(Node previous, Node current) {
        if (previous.getRange().isEmpty() || current.getRange().isEmpty()) {
            return false;
        }
        int prevEnd = previous.getRange().get().end.line;
        int curStart = current.getRange().get().begin.line;
        if (curStart <= prevEnd + 1) {
            return false;
        }
        Optional<Node> parent = current.getParentNode();
        if (parent.isEmpty()) {
            return false;
        }
        for (Comment comment : parent.get().getAllContainedComments()) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            int line = comment.getRange().get().begin.line;
            if (line >= prevEnd && line < curStart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a node has a line/block comment lexically before its start.
     *
     * <p>Line/block comments on nodes printed immediately after {@code =} or {@code ->} without a line
     * break can be re-attached to a different AST node on the next parse — use a continuation line
     * before printing such nodes.
     */
    static boolean hasLeadingLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        int commentLine = comment.getRange().get().begin.line;
        int nodeLine = node.getRange().get().begin.line;
        if (commentLine < nodeLine) {
            return true;
        }
        if (commentLine > nodeLine) {
            return false;
        }
        int commentEndColumn = comment.getRange().get().end.column;
        int nodeBeginColumn = node.getRange().get().begin.column;
        return commentEndColumn < nodeBeginColumn;
    }

    /** Returns true when a node has a same-line trailing line/block comment. */
    static boolean hasTrailingLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        int commentLine = comment.getRange().get().begin.line;
        int nodeLine = node.getRange().get().end.line;
        if (commentLine != nodeLine) {
            return false;
        }
        int commentBeginColumn = comment.getRange().get().begin.column;
        int nodeEndColumn = node.getRange().get().end.column;
        return commentBeginColumn > nodeEndColumn;
    }

    /** Returns true when any operand has a leading line/block comment. */
    static boolean anyOperandHasLeadingLineOrBlockComment(List<Expression> parts) {
        for (Expression p : parts) {
            if (hasLeadingLineOrBlockComment(p)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when any operand has a trailing line/block comment. */
    static boolean anyOperandHasTrailingLineOrBlockComment(List<Expression> parts) {
        for (Expression p : parts) {
            if (hasTrailingLineOrBlockComment(p)) {
                return true;
            }
        }
        return false;
    }

    /** Removes owned and orphan comments from a subtree clone. */
    static void removeAllCommentsFromTree(Node node) {
        new ArrayList<>(node.getOrphanComments()).forEach(Comment::remove);
        node.getComment().ifPresent(Comment::remove);
        for (Node child : new ArrayList<>(node.getChildNodes())) {
            if (!(child instanceof Comment)) {
                removeAllCommentsFromTree(child);
            }
        }
    }

    /**
     * Extracts misplaced orphan line comments from before a type name token.
     *
     * <p>JavaParser sometimes attaches a line comment that lexically follows {@code extends Type {}}
     * as an orphan <em>before</em> the type's simple name (between {@code class} and the name).
     * Relocate those comments into the type body so they are not printed twice and idempotency holds.
     */
    static List<Comment> extractLineCommentsMisplacedBeforeTypeName(ClassOrInterfaceDeclaration n) {
        if (n.getName().getRange().isEmpty()) {
            return List.of();
        }
        int nameLine = n.getName().getRange().get().begin.line;
        int nameCol = n.getName().getRange().get().begin.column;
        List<Comment> out = new ArrayList<>();
        for (Comment c : new ArrayList<>(n.getOrphanComments())) {
            if (!(c instanceof LineComment)) {
                continue;
            }
            if (c.getRange().isEmpty()) {
                continue;
            }
            int cl = c.getRange().get().begin.line;
            int cc = c.getRange().get().begin.column;
            if (cl == nameLine && cc < nameCol) {
                c.remove();
                out.add(c);
            }
        }
        out.sort(
                Comparator.comparingInt((Comment c) -> c.getRange().orElseThrow().begin.line)
                        .thenComparingInt(c -> c.getRange().orElseThrow().begin.column));
        return out;
    }

    /**
     * Extracts and deduplicates orphan line comments on the type-name line.
     *
     * <p>With {@code extends} / {@code implements}, JavaParser may duplicate the same opening-brace
     * line comment as several orphan nodes on the header line. Strip the whole line's line-comment
     * orphans and keep one copy per distinct comment text (rightmost wins).
     */
    static List<Comment> extractAndDedupeLineCommentsOnTypeNameLine(ClassOrInterfaceDeclaration n) {
        if (n.getName().getRange().isEmpty()) {
            return List.of();
        }
        int headerLine = n.getName().getRange().get().begin.line;
        List<LineComment> candidates = new ArrayList<>();
        for (Comment c : new ArrayList<>(n.getOrphanComments())) {
            if (c instanceof LineComment lc
                    && c.getRange().isPresent()
                    && c.getRange().get().begin.line == headerLine) {
                c.remove();
                candidates.add(lc);
            }
        }
        candidates.sort(Comparator.comparingInt(c -> c.getRange().orElseThrow().begin.column));
        Map<String, LineComment> uniqueByContent = new LinkedHashMap<>();
        for (LineComment lc : candidates) {
            uniqueByContent.put(lc.getContent(), lc);
        }
        List<Comment> out = new ArrayList<>(uniqueByContent.values());
        out.sort(Comparator.comparingInt(c -> c.getRange().orElseThrow().begin.column));
        return out;
    }

    /**
     * Removes duplicate header line comments mirrored onto type-clause entries.
     *
     * <p>Some headers carry the same trailing line comment on both the simple name and a type-clause
     * entry (for example, both {@code OperatorNot} and {@code extends SpelNodeImpl}). Remove the
     * duplicate from type-clause entries so we print it once and stay idempotent.
     */
    static void pruneDuplicatedHeaderLineCommentsOnTypeClauses(ClassOrInterfaceDeclaration n) {
        Optional<Comment> nameComment = n.getName().getComment();
        if (nameComment.isEmpty() || !(nameComment.get() instanceof LineComment) || nameComment.get().getRange().isEmpty()) {
            return;
        }
        Comment nc = nameComment.get();
        String content = nc.getContent();
        int line = nc.getRange().orElseThrow().begin.line;
        for (ClassOrInterfaceType t : n.getExtendedTypes()) {
            Optional<Comment> tc = t.getComment();
            if (tc.isPresent()
                    && tc.get() instanceof LineComment
                    && tc.get().getRange().isPresent()
                    && tc.get().getRange().orElseThrow().begin.line == line
                    && Objects.equals(tc.get().getContent(), content)) {
                tc.get().remove();
            }
        }
        for (ClassOrInterfaceType t : n.getImplementedTypes()) {
            Optional<Comment> tc = t.getComment();
            if (tc.isPresent()
                    && tc.get() instanceof LineComment
                    && tc.get().getRange().isPresent()
                    && tc.get().getRange().orElseThrow().begin.line == line
                    && Objects.equals(tc.get().getContent(), content)) {
                tc.get().remove();
            }
        }
    }

    /** Returns a comment candidate that should be hoisted before wrapped call arguments. */
    static Optional<Comment> hoistableArgumentComment(MethodCallExpr mc) {
        if (mc.getArguments().isEmpty()) {
            return Optional.empty();
        }
        if (mc.getArguments().size() > SINGLE_ARGUMENT_COUNT) {
            for (Expression argument : mc.getArguments()) {
                Optional<Comment> comment = argument.getComment();
                if (comment.isPresent() && isEmptyLineOrBlockComment(comment.get())) {
                    return comment;
                }
            }
            return Optional.empty();
        }
        Expression only = mc.getArguments().get(0);
        if (only instanceof LambdaExpr) {
            return Optional.empty();
        }
        if (hasLineOrBlockComment(only)) {
            return only.getComment();
        }
        return firstLineOrBlockCommentPrintedBeforeExpression(only);
    }

    /** Returns a base comment suitable for hoisting on wrapped method chains. */
    static Optional<Comment> hoistableWrappedChainBaseComment(Expression base) {
        Optional<Comment> comment = base.getComment();
        if (comment.isPresent() && isEmptyLineOrBlockComment(comment.get())) {
            return comment;
        }
        return Optional.empty();
    }

    /** Returns true when any part of a call chain contains line/block comments. */
    static boolean chainHasLineOrBlockComments(Expression base, List<MethodCallExpr> calls) {
        if (hasAnyLineOrBlockComment(base)) {
            return true;
        }
        for (MethodCallExpr mc : calls) {
            if (hasAnyLineOrBlockComment(mc) || hasAnyLineOrBlockComment(mc.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when any argument is a lambda with a block body. */
    static boolean hasBlockLambdaArgument(NodeList<? extends Expression> args) {
        for (Expression expression : args) {
            if (expression instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when comment is either line or block style. */
    private static boolean isLineOrBlock(Comment comment) {
        return comment instanceof LineComment || comment instanceof BlockComment;
    }
}
