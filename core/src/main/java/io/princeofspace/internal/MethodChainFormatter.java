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
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Formats method-call chains (fluent API calls). Delegates back to {@link LayoutContext} for
 * output primitives, comment handling, and recursive visitor dispatch.
 */
final class MethodChainFormatter {

    private final LayoutContext ctx;
    private final CommentUtils comments;

    /** Creates a method-chain formatter bound to shared layout context and comment helpers. */
    MethodChainFormatter(LayoutContext ctx, CommentUtils comments) {
        this.ctx = ctx;
        this.comments = comments;
    }

    /** Formats a method call, delegating to chain-aware rendering when scoped calls are detected. */
    void format(MethodCallExpr n, Void arg) {
        if (n.getScope().isEmpty()) {
            ctx.printOrphanCommentsBeforeThisChildNode(n);
            ctx.printComment(n.getComment(), arg);
            ctx.printTypeArgs(n, arg);
            ctx.print(n.getNameAsString());
            ctx.printArguments(n.getArguments(), arg);
            return;
        }
        MethodCallExpr outer = outermostCall(n);
        if (!outer.equals(n)) {
            return;
        }
        List<MethodCallExpr> calls = chainInOrder(outer);
        Optional<Expression> baseOpt = chainBase(outer);
        if (baseOpt.isEmpty()) {
            ctx.acceptDefault(n, arg);
            return;
        }
        Expression base = baseOpt.get();
        if (base instanceof TextBlockLiteralExpr) {
            printChainInlineWithInlineArgs(base, calls, arg);
            return;
        }
        boolean wrap = mustHardWrapChain(base, calls)
                || mustWrapChain(base, calls)
                || shouldWrapLambdaHeavyChain(base, calls)
                || comments.chainHasLineOrBlockComments(base, calls);
        if (!wrap) {
            ctx.printOrphanCommentsBeforeThisChildNode(n);
            printChainInline(base, calls, arg);
            return;
        }
        printChainBalancedOrNarrow(base, calls, arg);
    }

    /** Finds the outermost call in a scope-linked chain. */
    private static MethodCallExpr outermostCall(MethodCallExpr n) {
        MethodCallExpr cur = n;
        while (true) {
            Optional<Node> p = cur.getParentNode();
            if (p.isEmpty() || !(p.get() instanceof MethodCallExpr parent)) {
                break;
            }
            if (parent.getScope().isPresent() && Objects.equals(parent.getScope().get(), cur)) {
                cur = parent;
            } else {
                break;
            }
        }
        return cur;
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
        int w = ctx.est(base);
        for (MethodCallExpr mc : calls) {
            w += 1 + mc.getName().asString().length() + 2 + argsFlatWidth(mc.getArguments()) + 1;
        }
        return w;
    }

    /** Estimates one-line width of a comma-separated argument list. */
    int argsFlatWidth(NodeList<? extends Expression> args) {
        int w = 0;
        boolean first = true;
        for (Expression a : args) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += ctx.est(a);
        }
        return w;
    }

    /** Prints call arguments exactly as inline text without applying wrapping rules. */
    void printArgumentsInline(NodeList<? extends Expression> args) {
        ctx.print("(");
        for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
            ctx.print(i.next().toString());
            if (i.hasNext()) {
                ctx.print(", ");
            }
        }
        ctx.print(")");
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

    /** Prints a chain inline while forcing inline rendering of argument payload text. */
    void printChainInlineWithInlineArgs(Expression base, List<MethodCallExpr> calls, Void arg) {
        ctx.accept(base, arg);
        for (MethodCallExpr mc : calls) {
            ctx.print(".");
            ctx.printTypeArgs(mc, arg);
            ctx.print(mc.getNameAsString());
            printArgumentsInline(mc.getArguments());
        }
    }

    /**
     * Wrapped chains: one {@code .method()} per continuation line (leading-dot style), matching common
     * Kotlin / Prettier habits and keeping diffs one segment per line. Exception: a <strong>single</strong>
     * chained call with a {@link #isSimpleBase simple} receiver stays {@code Receiver.method(...)} on one
     * line so trivial {@code items.stream()} does not become two lines.
     */
    void printChainBalancedOrNarrow(Expression base, List<MethodCallExpr> calls, Void arg) {
        int lineStartColumn = ctx.column();
        Optional<Comment> hoistedBaseComment = comments.hoistableWrappedChainBaseComment(base);
        if (hoistedBaseComment.isPresent()) {
            printExpressionWithoutOwnComment(base, arg);
        } else {
            ctx.accept(base, arg);
        }

        if (calls.size() == 1
                && isSimpleBase(base)
                && !comments.hasLineOrBlockComment(calls.get(0))
                && !comments.hasLineOrBlockComment(calls.get(0).getName())
                && calls.get(0).getOrphanComments().isEmpty()) {
            MethodCallExpr only = calls.get(0);
            ctx.print(".");
            ctx.printTypeArgs(only, arg);
            ctx.accept(only.getName(), arg);
            if (comments.hasBlockLambdaArgument(only.getArguments())) {
                ctx.indentWithAlignToSafe(lineStartColumn + ctx.config().continuationIndentSize());
                try {
                    ctx.printArguments(only.getArguments(), arg);
                } finally {
                    ctx.unindent();
                }
            } else {
                ctx.printArguments(only.getArguments(), arg);
            }
            return;
        }

        ctx.println();
        ctx.printCont();
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
            Optional<Comment> hoistedComment = comments.hoistableArgumentComment(mc);
            if (comments.hasLineOrBlockComment(mc)) {
                ctx.printComment(mc.getComment(), arg);
            } else if (comments.hasLineOrBlockComment(mc.getName())) {
                ctx.printComment(mc.getName().getComment(), arg);
            } else if (hoistedComment.isPresent()) {
                ctx.printComment(hoistedComment, arg);
            }
            ctx.print(".");
            ctx.printTypeArgs(mc, arg);
            ctx.print(mc.getNameAsString());
            if (comments.hasBlockLambdaArgument(mc.getArguments())) {
                ctx.indentWithAlignToSafe(Math.max(contCol, lineStartColumn + ctx.config().continuationIndentSize()));
                try {
                    ctx.printArguments(mc.getArguments(), arg);
                } finally {
                    ctx.unindent();
                }
            } else if (hoistedComment.isPresent()) {
                printArgumentsWithoutComments(mc.getArguments(), arg);
            } else {
                ctx.printArguments(mc.getArguments(), arg);
            }
        }
        ctx.unindent();
    }

    private static boolean isSimpleBase(Expression base) {
        return base instanceof NameExpr
                || base instanceof FieldAccessExpr
                || base instanceof ThisExpr
                || base instanceof SuperExpr;
    }

    /** Returns true when the chain exceeds preferred line length budget. */
    boolean mustWrapChain(Expression base, List<MethodCallExpr> calls) {
        return ctx.column() + chainOneLineWidth(base, calls) > ctx.config().preferredLineLength();
    }

    /** Returns true when the chain exceeds hard max line length budget. */
    boolean mustHardWrapChain(Expression base, List<MethodCallExpr> calls) {
        return ctx.column() + chainOneLineWidth(base, calls) > ctx.config().maxLineLength();
    }

    /** Heuristic wrap trigger for chains that contain lambda arguments. */
    boolean shouldWrapLambdaHeavyChain(Expression base, List<MethodCallExpr> calls) {
        if (calls.size() <= 1) {
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
        return hasLambdaArgument && chainOneLineWidth(base, calls) > 60;
    }

    /** Prints an expression clone without its owned comment. */
    void printExpressionWithoutOwnComment(Expression expression, Void arg) {
        Expression copy = expression.clone();
        copy.removeComment();
        ctx.accept(copy, arg);
    }

    /** Prints argument clones with comments removed to avoid duplicated hoisted comments. */
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
