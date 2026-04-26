package io.princeofspace.internal;

import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.utils.StringEscapeUtils;
import io.princeofspace.model.WrapStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formats {@link BinaryExpr} nodes (boolean chains, string concatenation, arithmetic).
 * Delegates back to {@link LayoutContext} for output primitives, comment handling, and
 * recursive visitor dispatch.
 */
@SuppressWarnings("VoidUsed")
final class BinaryExprFormatter {
    private static final int MIN_LEAVES_FOR_FORCED_STRING_RECHUNK = 128;
    private static final int LOGICAL_OPERATOR_WITH_SPACES_WIDTH = 4; // e.g. " && " / " || "
    private static final int STRING_CONCAT_OPERATOR_WITH_SPACES_WIDTH = 3; // " + "

    private final LayoutContext ctx;
    private final CommentUtils comments;

    /** Creates a binary formatter bound to shared layout context and comment helpers. */
    BinaryExprFormatter(LayoutContext ctx, CommentUtils comments) {
        this.ctx = ctx;
        this.comments = comments;
    }

    /** Formats binary expressions with wrap-style-specific handling for comments and line budgets. */
    void format(BinaryExpr n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
            List<Expression> parts = new ArrayList<>();
            collectSameOp(n.getOperator(), (Expression) n, parts);
            int flat = ctx.column();
            for (Expression p : parts) {
                flat += WidthMeasurer.flatWidth(p, ctx.config()) + LOGICAL_OPERATOR_WITH_SPACES_WIDTH;
            }
            if (!comments.anyOperandHasLeadingLineOrBlockComment(parts)
                    && !comments.anyOperandHasTrailingLineOrBlockComment(parts)
                    && flat <= ctx.config().lineLength()) {
                ctx.accept(parts.get(0), arg);
                String os = n.getOperator().asString();
                for (int i = 1; i < parts.size(); i++) {
                    ctx.print(" ");
                    ctx.print(os);
                    ctx.print(" ");
                    ctx.accept(parts.get(i), arg);
                }
                return;
            }
            String os = n.getOperator().asString();
            if (ctx.config().wrapStyle() == WrapStyle.BALANCED || ctx.config().wrapStyle() == WrapStyle.NARROW) {
                boolean prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(0), arg);
                for (int i = 1; i < parts.size(); i++) {
                    boolean interOperandComment = comments.hasCommentBetweenNodes(parts.get(i - 1), parts.get(i));
                    if (comments.hasLeadingLineOrBlockComment(parts.get(i)) || interOperandComment) {
                        printBinaryChainOperandWithInterposedLeadingComments(parts.get(i), os, arg);
                        prevTrailing = false;
                    } else {
                        if (!prevTrailing) {
                            ctx.println();
                        }
                        ctx.printCont();
                        ctx.print(os);
                        ctx.print(" ");
                        prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(i), arg);
                    }
                }
            } else {
                // WIDE: greedy packing (continuation indent affects only column position, not line budget)
                printBinaryGreedy(parts, os, arg);
            }
            return;
        }
        if (n.getOperator() == BinaryExpr.Operator.BINARY_AND
                || n.getOperator() == BinaryExpr.Operator.BINARY_OR
                || n.getOperator() == BinaryExpr.Operator.XOR) {
            List<Expression> parts = new ArrayList<>();
            collectSameOp(n.getOperator(), (Expression) n, parts);
            int flat = ctx.column();
            for (Expression p : parts) {
                flat += WidthMeasurer.flatWidth(p, ctx.config()) + LOGICAL_OPERATOR_WITH_SPACES_WIDTH;
            }
            if (!comments.anyOperandHasLeadingLineOrBlockComment(parts)
                    && !comments.anyOperandHasTrailingLineOrBlockComment(parts)
                    && flat <= ctx.config().lineLength()) {
                ctx.accept(parts.get(0), arg);
                String os = n.getOperator().asString();
                for (int i = 1; i < parts.size(); i++) {
                    ctx.print(" ");
                    ctx.print(os);
                    ctx.print(" ");
                    ctx.accept(parts.get(i), arg);
                }
                return;
            }
            String os = n.getOperator().asString();
            if (ctx.config().wrapStyle() == WrapStyle.BALANCED || ctx.config().wrapStyle() == WrapStyle.NARROW) {
                boolean prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(0), arg);
                for (int i = 1; i < parts.size(); i++) {
                    boolean interOperandComment = comments.hasCommentBetweenNodes(parts.get(i - 1), parts.get(i));
                    if (comments.hasLeadingLineOrBlockComment(parts.get(i)) || interOperandComment) {
                        printBinaryChainOperandWithInterposedLeadingComments(parts.get(i), os, arg);
                        prevTrailing = false;
                    } else {
                        if (!prevTrailing) {
                            ctx.println();
                        }
                        ctx.printCont();
                        ctx.print(os);
                        ctx.print(" ");
                        prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(i), arg);
                    }
                }
            } else {
                printBinaryGreedy(parts, os, arg);
            }
            return;
        }
        if (n.getOperator() == BinaryExpr.Operator.PLUS) {
            // Literal-only + chains (including parenthesized balanced concat from deep chunking):
            // re-emit using the same chunk algorithm as visit(StringLiteralExpr), or idempotency
            // diverges. Uses iterative collection so balanced trees do not rely on flatten recursion.
            List<StringLiteralExpr> concatLeaves = new ArrayList<>();
            if (tryCollectPureStringConcatLeaves((Expression) n, concatLeaves)) {
                List<Expression> concatLeavesAsExpr = new ArrayList<>(concatLeaves);
                if (!comments.anyOperandHasLeadingLineOrBlockComment(concatLeavesAsExpr)
                        && !comments.anyOperandHasTrailingLineOrBlockComment(concatLeavesAsExpr)
                        && !anyInterOperandComments(concatLeavesAsExpr)) {
                    String merged = mergeStringLiteralValues(concatLeaves);
                    int max = ctx.config().lineLength();
                    int mergedQuotedLen = StringEscapeUtils.escapeJava(merged).length() + 2;
                    boolean mergedWouldOverflowSingleLiteral = ctx.column() + mergedQuotedLen > max;
                    boolean hasEscapesSensitiveToBoundaryPlacement = merged.indexOf('\\') >= 0;
                    if (mergedWouldOverflowSingleLiteral
                            && !hasEscapesSensitiveToBoundaryPlacement
                            && concatLeaves.size() >= MIN_LEAVES_FOR_FORCED_STRING_RECHUNK) {
                        ctx.emitChunkedStringLiteral(merged);
                        return;
                    }
                }
            }
            List<Expression> parts = new ArrayList<>();
            collectSameOp(BinaryExpr.Operator.PLUS, (Expression) n, parts);
            int flat = ctx.column();
            for (Expression p : parts) {
                flat += WidthMeasurer.flatWidth(p, ctx.config()) + STRING_CONCAT_OPERATOR_WITH_SPACES_WIDTH;
            }
            if (!comments.anyOperandHasLeadingLineOrBlockComment(parts)
                    && !comments.anyOperandHasTrailingLineOrBlockComment(parts)
                    && flat <= ctx.config().lineLength()) {
                ctx.accept(parts.get(0), arg);
                for (int i = 1; i < parts.size(); i++) {
                    ctx.print(" + ");
                    ctx.accept(parts.get(i), arg);
                }
                return;
            }
            // R1: WIDE greedy packing for + was column-based and could fail to reach a fixed point
            // (e.g. Spring withMessage with +// continuations, long annotation args). For non-inline
            // + chains, use the same one-infix-per-line layout as BALANCED (see
            // printPlusChainOnePerLineWithPlusSpine javadoc). Greedy remains for other operators
            // (e.g. &&) under WIDE.
            List<BinaryExpr> plusOrphanSplits = listLeftAssociativePlusSplitNodes(n);
            printPlusChainOnePerLineWithPlusSpine(parts, arg, plusOrphanSplits);
            return;
        }
        ctx.acceptDefault(n, arg);
    }

    /**
     * One infix and operand per line (continuation). This is the layout for all wrapped {@code +}
     * chains (WIDE, BALANCED, NARROW). Emits and removes any orphan line comments (e.g. empty {@code
     * //} after {@code +}) from the left-associative + spine; Greedy WIDE no longer applies to +
     * for idempotency.
     */
    private void printPlusChainOnePerLineWithPlusSpine(
            List<Expression> parts, Void arg, List<BinaryExpr> plusOrphanSplits) {
        // R4: a long string-literal first operand can overflow if printed at the chain's start
        // column. Inside a + chain we do not chunk the literal (see visit(StringLiteralExpr) +
        // isInsideStringConcatChain), so force a continuation before parts[0] when its flat width
        // would not fit. Pass-1 column-aware piece sizing in PrincePrettyPrinterVisitor keeps
        // re-parsed first operands within budget, so only the obvious overflow check is needed
        // here — a conservative "close to the limit" guard would spuriously break asserts where
        // the first operand was sized to fit at column 16 (continuation indent).
        Expression first = parts.get(0);
        if (first instanceof StringLiteralExpr) {
            int flat = WidthMeasurer.flatWidth(first, ctx.config());
            if (ctx.column() + flat > ctx.config().lineLength()) {
                ctx.println();
                ctx.printCont();
            }
        }
        boolean prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(0), arg);
        for (int i = 1; i < parts.size(); i++) {
            if (comments.hasLeadingLineOrBlockComment(parts.get(i))) {
                printBinaryChainOperandWithInterposedLeadingComments(parts.get(i), "+", arg);
                prevTrailing = false;
            } else {
                if (!prevTrailing) {
                    ctx.println();
                }
                ctx.printCont();
                ctx.print("+ ");
                if (i - 1 < plusOrphanSplits.size() && plusOrphanSplits.size() == parts.size() - 1) {
                    printOrphansFromNode(plusOrphanSplits.get(i - 1), arg);
                }
                prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(i), arg);
            }
        }
    }

    /**
     * Collects string literals from a pure string-concat expression tree in left-to-right order.
     * Handles {@link EnclosedExpr} wrappers (balanced {@code +} trees) iteratively.
     */
    private static boolean tryCollectPureStringConcatLeaves(Expression root, List<StringLiteralExpr> out) {
        ArrayDeque<Expression> stack = new ArrayDeque<>();
        Expression cur = root;
        while (true) {
            while (cur instanceof EnclosedExpr enc) {
                cur = enc.getInner();
            }
            if (cur instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
                stack.push(b.getRight());
                cur = b.getLeft();
            } else if (cur instanceof StringLiteralExpr sl) {
                out.add(sl);
                if (stack.isEmpty()) {
                    return true;
                }
                cur = stack.pop();
            } else {
                out.clear();
                return false;
            }
        }
    }

    private static String mergeStringLiteralValues(List<StringLiteralExpr> parts) {
        StringBuilder sb = new StringBuilder();
        for (StringLiteralExpr p : parts) {
            sb.append(p.getValue());
        }
        return sb.toString();
    }

    private boolean anyInterOperandComments(List<Expression> parts) {
        for (int i = 1; i < parts.size(); i++) {
            if (comments.hasCommentBetweenNodes(parts.get(i - 1), parts.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flattens adjacent binary nodes that share the same operator. Implemented iteratively so
     * very deep chains (e.g. long string-concat trees after chunking) cannot overflow the stack.
     */
    private static void collectSameOp(BinaryExpr.Operator op, Expression e, List<Expression> out) {
        ArrayDeque<Expression> stack = new ArrayDeque<>();
        Expression cur = e;
        while (true) {
            if (cur instanceof BinaryExpr b && b.getOperator() == op) {
                stack.push(b.getRight());
                cur = b.getLeft();
            } else {
                out.add(cur);
                if (stack.isEmpty()) {
                    return;
                }
                cur = stack.pop();
            }
        }
    }

    /** Greedy packing for binary operator chains: pack as many operands per line as fit. */
    void printBinaryGreedy(List<Expression> parts, String op, Void arg) {
        printBinaryGreedy(parts, op, arg, ctx.config().lineLength());
    }

    /** Greedily packs binary operands while respecting line length. */
    void printBinaryGreedy(List<Expression> parts, String op, Void arg, int budget) {
        boolean prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(0), arg);
        int used = ctx.column();
        for (int i = 1; i < parts.size(); i++) {
            int opLen = op.length() + 2; // " op "
            int partLen = WidthMeasurer.flatWidth(parts.get(i), ctx.config());
            boolean leadingComment = comments.hasLeadingLineOrBlockComment(parts.get(i));
            boolean interOperandComment = comments.hasCommentBetweenNodes(parts.get(i - 1), parts.get(i));
            boolean overBudget = used + opLen + partLen > budget;
            if (leadingComment || interOperandComment) {
                printBinaryChainOperandWithInterposedLeadingComments(parts.get(i), op, arg);
                prevTrailing = false;
                used = ctx.column();
                continue;
            }
            if (prevTrailing || overBudget) {
                if (!prevTrailing) {
                    ctx.println();
                }
                ctx.printCont();
                ctx.print(op);
                ctx.print(" ");
                used = ctx.column();
            } else {
                ctx.print(" ");
                ctx.print(op);
                ctx.print(" ");
                used += opLen;
            }
            prevTrailing = printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(parts.get(i), arg);
            used = ctx.column();
        }
    }

    private void printOrphansFromNode(BinaryExpr n, Void arg) {
        for (Comment c : new ArrayList<>(n.getOrphanComments())) {
            ctx.accept(c, arg);
            if (c.isOrphan()) {
                c.remove();
            }
        }
    }

    /**
     * Left-associative {@code +} tree only: the {@code p0 + p1 + ...} spine from the deepest
     * subexpression up to the root, bottom-first order (split 0 = between parts 0|1, ...).
     */
    private static List<BinaryExpr> listLeftAssociativePlusSplitNodes(BinaryExpr top) {
        if (top.getOperator() != BinaryExpr.Operator.PLUS) {
            return List.of();
        }
        BinaryExpr bottom = top;
        while (bottom.getLeft() instanceof BinaryExpr lb && lb.getOperator() == BinaryExpr.Operator.PLUS) {
            bottom = lb;
        }
        List<BinaryExpr> fromBottom = new ArrayList<>();
        BinaryExpr cur = bottom;
        while (true) {
            fromBottom.add(cur);
            if (!(cur.getParentNode()
                    .orElse(null) instanceof BinaryExpr parent)) {
                break;
            }
            if (parent.getOperator() != BinaryExpr.Operator.PLUS
                    || !Objects.equals(parent.getLeft(), cur)) {
                break;
            }
            cur = parent;
        }
        return fromBottom;
    }

    private boolean printExprWithTrailingCommentAfterWithMethodChainContinuationIndent(
            Expression expr, Void arg) {
        if (!isMethodChainExpression(stripEnclosed(expr))) {
            return printExprWithTrailingCommentAfter(expr, arg);
        }
        // Push two indent levels so a wrapped method-chain operand lands one indentSize past the
        // operator-line indent (operator at base+continuation; chain segments at operator + indentSize).
        // See docs/canonical-formatting-rules.md Rule 7 and TDR-015.
        ctx.indent();
        ctx.indent();
        try {
            return printExprWithTrailingCommentAfter(expr, arg);
        } finally {
            ctx.unindent();
            ctx.unindent();
        }
    }

    private static Expression stripEnclosed(Expression e) {
        while (e instanceof EnclosedExpr enclosedExpr) {
            e = enclosedExpr.getInner();
        }
        return e;
    }

    private static boolean isMethodChainExpression(Expression e) {
        return e instanceof MethodCallExpr methodCallExpr && methodCallExpr.getScope().isPresent();
    }

    /**
     * Prints an expression, but if it carries a trailing line/block comment (same line in source),
     * strips the comment from the node, prints the expression, then appends the comment on the same
     * line. This keeps idioms like {@code "str" + // ...} stable: the comment stays <em>after</em>
     * the expression rather than being flipped to leading position by {@code printComment()}.
     *
     * @return {@code true} if a trailing comment was emitted (line was ended by the comment)
     */
    boolean printExprWithTrailingCommentAfter(Expression expr, Void arg) {
        if (!comments.hasTrailingLineOrBlockComment(expr)) {
            ctx.accept(expr, arg);
            return false;
        }
        Comment trailing = expr.getComment().get();
        expr.setComment(null);
        ctx.accept(expr, arg);
        ctx.print(" ");
        ctx.accept(trailing, null);
        return true;
    }

    /**
     * Binary chain operands with leading line/block comments must not print {@code "|| //"} (or
     * {@code "+ //"}) on one line: line comments end with a newline, so print orphan + owned
     * comments first, then the operator, then a comment-free clone of the operand.
     */
    void printBinaryChainOperandWithInterposedLeadingComments(
            Expression operand, String op, Void arg) {
        ctx.println();
        ctx.printCont();
        ctx.printOrphanCommentsBeforeThisChildNode(operand);
        ctx.printComment(operand.getComment(), arg);
        ctx.print(op);
        ctx.print(" ");
        Expression stripped = operand.clone();
        comments.removeAllCommentsFromTree(stripped);
        ctx.accept(stripped, arg);
    }
}
