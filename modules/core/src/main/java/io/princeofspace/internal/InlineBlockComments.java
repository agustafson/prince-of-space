package io.princeofspace.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.printer.SourcePrinter;
import com.github.javaparser.utils.PositionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Keeps inline block comments beside their source tokens, independently of AST comment ownership. */
final class InlineBlockComments {
    private static final DataKey<Integer> COMMENT_WIDTH = new DataKey<>() {};
    private final Map<Position, String> before = new HashMap<>();
    private final Map<Position, String> after = new HashMap<>();
    private final Map<Position, String> emptyArguments = new HashMap<>();

    void collect(CompilationUnit cu) {
        var comments = cu.getAllContainedComments();
        if (comments.stream().noneMatch(BlockComment.class::isInstance)) {
            return;
        }
        Map<Position, Node> starts = new HashMap<>();
        Map<Position, Node> ends = new HashMap<>();
        Map<Position, MethodCallExpr> emptyCalls = new HashMap<>();
        cu.walk(node -> {
            if (node instanceof MethodCallExpr call && call.getArguments().isEmpty()) {
                call.getEnd().ifPresent(position -> emptyCalls.put(position, call));
            }
            if (canPrintAround(node)) {
                node.getBegin().ifPresent(position -> starts.put(position, node));
                node.getEnd().ifPresent(position -> ends.put(position, node));
            }
        });
        Set<Position> detached = new HashSet<>();
        PositionUtils.sortByBeginPosition(comments);
        for (var comment : comments) {
            if (!(comment instanceof BlockComment) || comment.getTokenRange().isEmpty()) {
                continue;
            }
            JavaToken token = comment.getTokenRange().orElseThrow().getBegin();
            JavaToken left = previousCodeToken(token);
            JavaToken right = nextCodeToken(token);
            if (token.getRange().isEmpty() || left.getRange().isEmpty() || right.getRange().isEmpty()) {
                continue;
            }
            var range = token.getRange().orElseThrow();
            Position leftEnd = left.getRange().orElseThrow().end;
            Position rightBegin = right.getRange().orElseThrow().begin;
            if (detached.contains(range.begin) || range.begin.line != range.end.line) {
                continue;
            }
            // Empty calls have no argument node to anchor inline comments.
            if (left.getText().equals("(") && right.getText().equals(")")
                    && leftEnd.line == range.begin.line && rightBegin.line == range.end.line
                    && emptyCalls.containsKey(rightBegin)) {
                int separatorWidth = emptyArguments.containsKey(rightBegin) ? 1 : 0;
                emptyArguments.merge(rightBegin, token.getText(), (a, b) -> a + " " + b);
                addWidth(emptyCalls.get(rightBegin), token.getText().length() + separatorWidth);
            } else if (leftEnd.line == range.begin.line && ends.containsKey(leftEnd)) {
                // R10: `value /* label */,` describes this value even when JavaParser owns it on the next one.
                after.merge(leftEnd, token.getText(), (a, b) -> a + " " + b);
                addWidth(ends.get(leftEnd), token.getText().length() + 1);
            } else if (rightBegin.line == range.end.line && starts.containsKey(rightBegin)) {
                before.merge(rightBegin, token.getText(), (a, b) -> a + " " + b);
                addWidth(starts.get(rightBegin), token.getText().length() + 1);
            } else {
                continue;
            }
            detached.add(range.begin);
        }
        if (detached.isEmpty()) {
            return;
        }
        // A parser comment can be mirrored on several nodes; remove all owners, including orphans.
        cu.walk(node -> {
            node.getComment().filter(c -> c.getBegin().map(detached::contains).orElse(false))
                    .ifPresent(c -> node.removeComment());
            for (var orphan : new ArrayList<>(node.getOrphanComments())) {
                if (orphan.getBegin().map(detached::contains).orElse(false)) {
                    orphan.remove();
                }
            }
        });
    }

    /** Only detach comments at boundaries emitted through LayoutContext or the simple-name visitor. */
    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private static boolean canPrintAround(Node node) {
        if (node instanceof Parameter || node instanceof SimpleName) {
            return true;
        }
        if (!(node instanceof Expression)) {
            return false;
        }
        Node parent = node.getParentNode().orElse(null);
        // A call's receiver is also a child expression, but is not an argument-list boundary.
        if (parent instanceof MethodCallExpr call && call.getScope().orElse(null) == node) {
            return false;
        }
        if (parent instanceof ObjectCreationExpr creation && creation.getScope().orElse(null) == node) {
            return false;
        }
        return parent instanceof NodeWithArguments<?> || parent instanceof ArrayInitializerExpr
                || parent instanceof VariableDeclarator || parent instanceof ExpressionStmt
                || parent instanceof ReturnStmt || parent instanceof ThrowStmt;
    }

    private static void addWidth(Node node, int width) {
        node.setData(COMMENT_WIDTH, width(node) + width);
        node.getParentNode().ifPresent(parent -> addWidth(parent, width));
    }

    static int width(Node node) {
        return node.containsData(COMMENT_WIDTH) ? node.getData(COMMENT_WIDTH) : 0;
    }

    private static JavaToken previousCodeToken(JavaToken token) {
        JavaToken previous = token;
        while (previous.getPreviousToken().isPresent()) {
            previous = previous.getPreviousToken().orElseThrow();
            if (!previous.getCategory().isWhitespaceOrComment()) {
                break;
            }
        }
        return previous;
    }

    private static JavaToken nextCodeToken(JavaToken token) {
        JavaToken next = token;
        while (next.getNextToken().isPresent()) {
            next = next.getNextToken().orElseThrow();
            if (!next.getCategory().isWhitespaceOrComment()) {
                break;
            }
        }
        return next;
    }

    void printBefore(Node node, SourcePrinter printer) {
        if (before.isEmpty()) {
            return;
        }
        node.getBegin().map(before::remove).ifPresent(text -> printer.print(text + " "));
    }

    void printAfter(Node node, SourcePrinter printer) {
        if (after.isEmpty()) {
            return;
        }
        node.getEnd().map(after::remove).ifPresent(text -> printer.print(" " + text));
    }

    void printEmptyArguments(MethodCallExpr call, SourcePrinter printer) {
        if (emptyArguments.isEmpty()) {
            return;
        }
        call.getEnd().map(emptyArguments::remove).ifPresent(printer::print);
    }
}
