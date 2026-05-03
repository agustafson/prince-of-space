package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Formats method-call chains (fluent API calls) per Rule 7 in {@code docs/canonical-formatting-rules.md}.
 *
 * <p>JavaParser represents chains as nested {@link MethodCallExpr} nodes: {@code a.b().c()} has {@code
 * c}'s scope pointing at {@code b()}, and so on. We walk the scope links to build an ordered list of
 * calls, decide inline vs wrapped from width (R4) and {@code wrapStyle} (R5), and use {@link
 * LayoutContext#printChainIndent} for wrapped segments (R7: one {@code indentSize} per segment, not
 * {@code 2 * indentSize} continuation — see TDR-015).
 *
 * <p>Delegated {@link FieldAccessExpr} / unscoped calls are not "chains" in this class; the visitor routes
 * those normally.
 */
@SuppressWarnings("VoidUsed")
final class MethodChainFormatter {
    private static final int SINGLE_ITEM_COUNT = 1;
    private static final int LAMBDA_HEAVY_CHAIN_WRAP_TRIGGER_WIDTH = 60;

    private final LayoutContext ctx;

    /** Creates a method-chain formatter bound to shared layout context. */
    MethodChainFormatter(LayoutContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Formats a method call. Unscoped {@code name(args)} is not a chain (R7). If this node is not the
     * outermost call of a scope-linked sequence, the outer visitor pass already printed the chain — return
     * without emitting again (R1: duplicate output would break idempotency).
     */
    void format(MethodCallExpr n, Void arg) {
        if (n.getScope().isEmpty()) {
            ctx.printOrphanCommentsBeforeThisChildNode(n);
            ctx.printComment(n.getComment(), arg);
            ctx.printTypeArgs(n, arg);
            ctx.print(n.getNameAsString());
            ctx.printArguments(n.getArguments(), arg);
            return;
        }
        // Cheap dispatch: only the *outermost* call in a chain emits anything; the inner calls are
        // visited recursively through the chain printers, but JavaParser's pretty-printer enters this
        // override on every {@link MethodCallExpr#accept}. If our parent is also a {@link MethodCallExpr}
        // and we are its scope, the chain is already being printed from the outer call — return now and
        // skip the (otherwise repeated) {@link #outermostCall} walk plus chain collection. This avoids
        // O(depth^2) work on long chains.
        @Nullable Node parent = n.getParentNode().orElse(null);
        if (parent instanceof MethodCallExpr parentCall && parentCall.getScope().isPresent()) {
            // Reference equality intentional: AST nodes are identity-compared, and the parent's
            // scope expression is the very same Node instance as {@code n} when {@code n} is a
            // non-outermost call inside a chain. {@link Node#equals} is a deep structural compare
            // (EqualsVisitor) — using it here would defeat the whole point of this fast-path.
            @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
            boolean nIsParentScope = parentCall.getScope().get() == n;
            if (nIsParentScope) {
                return;
            }
        }
        List<MethodCallExpr> calls = chainInOrder(n);
        Optional<Expression> baseOpt = chainBase(n);
        if (baseOpt.isEmpty()) {
            ctx.acceptDefaultBinaryExprOrMethodCall(n, arg);
            return;
        }
        Expression base = baseOpt.get();
        boolean wrap = mustWrapChain(base, calls)
                || shouldWrapLambdaHeavyChain(base, calls)
                || CommentUtils.chainHasLineOrBlockComments(base, calls);
        if (!wrap) {
            ctx.printOrphanCommentsBeforeThisChildNode(n);
            printChainInline(base, calls, arg);
            return;
        }
        printChainBalancedOrNarrow(base, calls, arg);
    }

    /** Innermost call first: {@code [stream, filter]} for {@code items.stream().filter()}. */
    private static List<MethodCallExpr> chainInOrder(MethodCallExpr outer) {
        ArrayList<MethodCallExpr> rev = new ArrayList<>();
        MethodCallExpr c = outer;
        while (true) {
            rev.add(c);
            if (c.getScope().isEmpty()) {
                break;
            }
            Expression sc = c.getScope().get();
            if (sc instanceof MethodCallExpr mc) {
                if (mc.getScope().isEmpty()) {
                    break;
                }
                c = mc;
            } else {
                break;
            }
        }
        ArrayList<MethodCallExpr> ord = new ArrayList<>();
        for (int i = rev.size() - 1; i >= 0; i--) {
            ord.add(rev.get(i));
        }
        return ord;
    }

    private static Optional<Expression> chainBase(MethodCallExpr outer) {
        if (outer.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression e = outer.getScope().get();
        while (e instanceof MethodCallExpr mc && mc.getScope().isPresent()) {
            e = mc.getScope().get();
        }
        return Optional.of(e);
    }

    /** Estimates one-line width of a full method chain from base through final call. */
    int chainOneLineWidth(Expression base, List<MethodCallExpr> calls) {
        int w = WidthMeasurer.flatWidth(base, ctx.config());
        for (MethodCallExpr mc : calls) {
            w += 1 + mc.getName().asString().length() + 2 + argsFlatWidth(mc.getArguments()) + 1;
        }
        return w;
    }

    /** Estimates one-line width of a comma-separated argument list. */
    int argsFlatWidth(NodeList<? extends Expression> args) {
        return WidthMeasurer.argumentsFlatWidthForMethodCalls(args, ctx.config());
    }

    /** Prints a chain on one physical line. */
    void printChainInline(Expression base, List<MethodCallExpr> calls, Void arg) {
        ctx.accept(base, arg);
        for (MethodCallExpr mc : calls) {
            ctx.print(".");
            ctx.printTypeArgs(mc, arg);
            ctx.print(mc.getNameAsString());
            ctx.printArguments(mc.getArguments(), arg);
        }
    }

    /**
     * R7 + R5: wrapped chains — one {@code .method()} per continuation line (leading-dot), indent via
     * {@link LayoutContext#printChainIndent} (one block indent step, TDR-015). R10: base/method
     * comments can be hoisted so they stay near the right segment. Exception: a single call after a
     * {@link #isSimpleBase simple} receiver stays {@code receiver.method(...)} on one line.
     */
    void printChainBalancedOrNarrow(Expression base, List<MethodCallExpr> calls, Void arg) {
        Optional<Comment> hoistedBaseComment = CommentUtils.hoistableWrappedChainBaseComment(base);
        if (hoistedBaseComment.isPresent()) {
            printExpressionWithoutOwnComment(base, arg);
        } else {
            ctx.accept(base, arg);
        }

        if (calls.size() == SINGLE_ITEM_COUNT
                && isSimpleBase(base)
                && !CommentUtils.hasLineOrBlockComment(calls.get(0))
                && !CommentUtils.hasLineOrBlockComment(calls.get(0).getName())
                && calls.get(0).getOrphanComments().isEmpty()) {
            MethodCallExpr only = calls.get(0);
            ctx.print(".");
            ctx.printTypeArgs(only, arg);
            ctx.accept(only.getName(), arg);
            ctx.printArguments(only.getArguments(), arg);
            return;
        }

        ctx.println();
        ctx.printChainIndent();
        int contCol = ctx.column();
        ctx.indentWithAlignToSafe(contCol);
        for (int i = 0; i < calls.size(); i++) {
            MethodCallExpr mc = calls.get(i);
            if (i > 0) {
                ctx.println();
            }
            if (i == calls.size() - 1 && hoistedBaseComment.isPresent()) {
                ctx.printComment(hoistedBaseComment, arg);
            }
            ctx.printOrphanCommentsBeforeThisChildNode(mc);
            Optional<Comment> hoistedComment = CommentUtils.hoistableArgumentComment(mc);
            if (CommentUtils.hasLineOrBlockComment(mc)) {
                ctx.printComment(mc.getComment(), arg);
            } else if (CommentUtils.hasLineOrBlockComment(mc.getName())) {
                ctx.printComment(mc.getName().getComment(), arg);
            } else if (hoistedComment.isPresent()) {
                ctx.printComment(hoistedComment, arg);
            }
            ctx.print(".");
            ctx.printTypeArgs(mc, arg);
            ctx.print(mc.getNameAsString());
            if (CommentUtils.hasBlockLambdaArgument(mc.getArguments())) {
                ctx.indentWithAlignToSafe(contCol);
                try {
                    ctx.printArguments(mc.getArguments(), arg);
                } finally {
                    ctx.unindent();
                }
            } else if (hoistedComment.isPresent()) {
                // R10: comment was printed above; re-print args without duplicating per-arg comments.
                printArgumentsWithoutComments(mc.getArguments(), arg);
            } else {
                ctx.printArguments(mc.getArguments(), arg);
            }
        }
        ctx.unindent();
    }

    // R7 exception: "items.stream()"-style — simple receiver + one call stays on one line when unwrapped.
    private static boolean isSimpleBase(Expression base) {
        return base instanceof NameExpr
                || base instanceof FieldAccessExpr
                || base instanceof ThisExpr
                || base instanceof SuperExpr;
    }

    // R4: current column + one-line width estimate vs lineLength; includes flatWidth of base and each call.
    boolean mustWrapChain(Expression base, List<MethodCallExpr> calls) {
        return ctx.column() + chainOneLineWidth(base, calls) > ctx.config().lineLength();
    }

    /**
     * R4 heuristics: multi-segment chain with a lambda in some argument and rough width &gt; threshold —
     * forces wrap even if a greedy one-line width estimate might still fit, so vertical structure matches
     * real edited code. Skips when the chain is an operand of {@link BinaryExpr} or {@link ConditionalExpr}
     * (R6 / ternary layout owns those positions).
     */
    boolean shouldWrapLambdaHeavyChain(Expression base, List<MethodCallExpr> calls) {
        if (calls.size() <= SINGLE_ITEM_COUNT) {
            return false;
        }
        Optional<Node> parent = calls.get(calls.size() - 1).getParentNode();
        if (parent.isPresent() && (parent.get() instanceof BinaryExpr || parent.get() instanceof ConditionalExpr)) {
            return false;
        }
        boolean hasLambdaArgument = false;
        for (MethodCallExpr call : calls) {
            for (Expression argument : call.getArguments()) {
                if (argument instanceof LambdaExpr) {
                    hasLambdaArgument = true;
                    break;
                }
            }
            if (hasLambdaArgument) {
                break;
            }
        }
        return hasLambdaArgument && chainOneLineWidth(base, calls) > LAMBDA_HEAVY_CHAIN_WRAP_TRIGGER_WIDTH;
    }

    /**
     * Prints an expression clone without its owned comment.
     *
     * <p>The clone has no {@linkplain Node#getParentNode() parent}; ancestor-sensitive layout inside the subtree may
     * differ from printing the original node (code review #29).
     */
    void printExpressionWithoutOwnComment(Expression expression, Void arg) {
        Expression copy = expression.clone();
        copy.removeComment();
        ctx.accept(copy, arg);
    }

    /**
     * Prints argument clones with comments removed to avoid duplicated hoisted comments.
     *
     * <p>Each argument uses a parent-less subtree clone; see {@link #printExpressionWithoutOwnComment(Expression, Void)}.
     */
    void printArgumentsWithoutComments(NodeList<? extends Expression> arguments, Void arg) {
        NodeList<Expression> copies = new NodeList<>();
        for (Expression expression : arguments) {
            Expression copy = expression.clone();
            copy.removeComment();
            for (Comment comment : new ArrayList<>(copy.getAllContainedComments())) {
                comment.remove();
            }
            copies.add(copy);
        }
        ctx.printArguments(copies, arg);
    }
}
