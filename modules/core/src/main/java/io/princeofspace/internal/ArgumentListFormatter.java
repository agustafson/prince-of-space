package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Width-aware formatting for comma-separated argument lists, formal parameters, and type parameter
 * / type argument angle-bracket lists. Used by {@link PrincePrettyPrinterVisitor} and shares
 * {@link LayoutContext} for output and recursive dispatch.
 */
@SuppressWarnings("VoidUsed")
final class ArgumentListFormatter {
    private static final int SINGLE_ITEM_COUNT = 1;
    private static final int CLOSING_PAREN_INLINE_RESERVED_WIDTH = 3; // ") {" / ");"


    private final LayoutContext ctx;
    private final FormatterConfig fmt;
    private final CommentUtils commentUtils;
    private final MethodChainFormatter methodChainFormatter;

    ArgumentListFormatter(
            LayoutContext ctx,
            FormatterConfig fmt,
            CommentUtils commentUtils,
            MethodChainFormatter methodChainFormatter) {
        this.ctx = ctx;
        this.fmt = fmt;
        this.commentUtils = commentUtils;
        this.methodChainFormatter = methodChainFormatter;
    }

    /**
     * Returns {@code true} when the argument list does not fit on the remainder of the current
     * line at the opening {@code (}.
     */
    boolean argsNeedWrap(NodeList<? extends Expression> args) {
        int width = ctx.column() + 1 + methodChainFormatter.argsFlatWidth(args);
        return width > fmt.lineLength();
    }

    /**
     * Prints a comma-separated list of expressions, applying the same wrapping rules as method
     * call arguments (including comment-aware and greedy wide wrapping).
     */
    void printCommaSeparatedExprs(NodeList<? extends Expression> args, Void arg) {
        if (args.size() == SINGLE_ITEM_COUNT
                && (commentUtils.hasLeadingLineOrBlockComment(args.get(0))
                        || commentUtils.hasAnyLineOrBlockCommentOnLambda(args.get(0)))) {
            ctx.println();
            ctx.printCont();
            ctx.accept(args.get(0), arg);
            return;
        }
        if (args.size() > SINGLE_ITEM_COUNT && commentUtils.hasLineOrBlockComment(args.get(0))) {
            ctx.println();
            ctx.printCont();
            if (fmt.wrapStyle() == WrapStyle.WIDE) {
                int extraLastLine = fmt.closingParenOnNewLine() ? 2 : 0;
                printGreedyCommaLines(args, arg, 0, false, extraLastLine);
            } else {
                for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                    printArgumentWithOptionalBlockLambdaIndent(i.next(), arg);
                    if (i.hasNext()) {
                        ctx.print(",");
                        ctx.println();
                        ctx.printCont();
                    }
                }
            }
            return;
        }
        if (!argsNeedWrap(args)) {
            for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            return;
        }
        if (args.size() == SINGLE_ITEM_COUNT) {
            ctx.accept(args.get(0), arg);
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int extraLastLine =
                    fmt.closingParenOnNewLine() ? 2 : 0; // ")" on its own line — no width reserved on last arg line
            printGreedyCommaLines(args, arg, 0, false, extraLastLine);
        } else {
            for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                ctx.println();
                ctx.printCont();
                printArgumentWithOptionalBlockLambdaIndent(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(",");
                }
            }
        }
    }

    private void printArgumentWithOptionalBlockLambdaIndent(Expression expression, Void arg) {
        if (expression instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt) {
            int lambdaColumn = ctx.column();
            ctx.indentWithAlignToSafe(lambdaColumn);
            try {
                ctx.accept(expression, arg);
            } finally {
                ctx.unindent();
            }
        } else {
            ctx.accept(expression, arg);
        }
    }

    /**
     * Greedy comma-separated expressions. Uses {@link LayoutContext#column()} after continuation
     * indent so {@code continuationIndentSize} does not change wrap positions vs other configs
     * (only indent).
     *
     * @param extraLastLineBudget when the closing delimiter is printed on its own line, extra width
     *     allowed on the last content line (no need to reserve for {@code )} on that line).
     */
    void printGreedyCommaLines(
            NodeList<? extends Expression> args,
            Void arg,
            int trailingWidth,
            boolean avoidLoneLastItem,
            int extraLastLineBudget) {
        boolean first = true;
        int budget = fmt.lineLength() - trailingWidth;
        Iterator<? extends Expression> iter = args.iterator();
        int remaining = args.size();
        @Nullable Expression previous = null;
        while (iter.hasNext()) {
            Expression e = iter.next();
            int expressionWidth = (e instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt)
                    ? e.toString().length()
                    : WidthMeasurer.flatWidth(e, fmt);
            int need = expressionWidth + (first ? 0 : 2);
            boolean shouldWrapForLoneLastItem = avoidLoneLastItem && !first && remaining == 2;
            boolean hasInterveningComment = previous != null && commentUtils.hasCommentBetweenNodes(previous, e);
            boolean currentHasLeadingComment = e.getComment().isPresent();
            int lineBudget = budget + (extraLastLineBudget > 0 && remaining == 1 ? extraLastLineBudget : 0);
            if (!first
                    && (ctx.column() + need > lineBudget
                            || wouldExceedLineLength(need)
                            || shouldWrapForLoneLastItem
                            || hasInterveningComment
                            || currentHasLeadingComment)) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
            } else if (!first) {
                ctx.print(", ");
            }
            ctx.accept(e, arg);
            first = false;
            remaining--;
            previous = e;
        }
    }

    /** Estimated width of formal parameters if printed on one line with {@code ", "} separators. */
    int paramsFlatWidth(NodeList<Parameter> ps) {
        int w = 0;
        boolean first = true;
        for (Parameter p : ps) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += p.toString().length();
        }
        return w;
    }

    /** Whether formal parameters need to wrap given the current column and width limits. */
    boolean paramsNeedWrap(NodeList<Parameter> ps) {
        int width = ctx.column() + 1 + paramsFlatWidth(ps);
        return width > fmt.lineLength();
    }

    /**
     * Whether appending {@code additionalWidth} characters on the current line would exceed the
     * configured line length.
     */
    boolean wouldExceedLineLength(int additionalWidth) {
        return ctx.wouldExceedLineLength(additionalWidth);
    }

    /** Prints formal parameters inside {@code (...)} with wrapping consistent with formatter config. */
    void printParametersList(NodeList<Parameter> ps, Void arg) {
        if (isNullOrEmpty(ps)) {
            return;
        }
        if (!paramsNeedWrap(ps)) {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int n = ps.size();
            boolean first = true;
            for (int idx = 0; idx < n; idx++) {
                Parameter p = ps.get(idx);
                int need = p.toString().length() + (first ? 0 : 2);
                boolean isLast = idx == n - 1;
                int lineBudget = fmt.lineLength();
                if (isLast) {
                    // When ")" stays on the last param line, reserve ") {" / ");"; when ")" is alone on
                    // the next line, that width is not needed on the param line.
                    lineBudget +=
                            fmt.closingParenOnNewLine()
                                    ? CLOSING_PAREN_INLINE_RESERVED_WIDTH
                                    : -CLOSING_PAREN_INLINE_RESERVED_WIDTH;
                }
                if (first && (ctx.column() + need > lineBudget || wouldExceedLineLength(need))) {
                    ctx.println();
                    ctx.printCont();
                } else if (!first && (ctx.column() + need > lineBudget || wouldExceedLineLength(need))) {
                    ctx.print(",");
                    ctx.println();
                    ctx.printCont();
                } else if (!first) {
                    ctx.print(", ");
                }
                ctx.accept(p, arg);
                first = false;
            }
        } else {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                ctx.println();
                ctx.printCont();
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(",");
                }
            }
        }
    }

    /**
     * Like {@link #printParametersList} but for lambda formal parameters: wrapped lines indent at
     * {@code openParenStartColumn + continuationIndentSize}, and the closing {@code )} is aligned
     * to {@code openParenStartColumn} by the caller.
     */
    void printParametersListForLambda(NodeList<Parameter> ps, Void arg, int openParenStartColumn) {
        if (isNullOrEmpty(ps)) {
            return;
        }
        if (!paramsNeedWrap(ps)) {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int n = ps.size();
            boolean first = true;
            for (int idx = 0; idx < n; idx++) {
                Parameter p = ps.get(idx);
                int need = p.toString().length() + (first ? 0 : 2);
                boolean isLast = idx == n - 1;
                int lineBudget = fmt.lineLength();
                if (isLast) {
                    lineBudget +=
                            fmt.closingParenOnNewLine()
                                    ? CLOSING_PAREN_INLINE_RESERVED_WIDTH
                                    : -CLOSING_PAREN_INLINE_RESERVED_WIDTH;
                }
                if (first && (ctx.column() + need > lineBudget || wouldExceedLineLength(need))) {
                    ctx.println();
                    lambdaParamContinuationToColumn(openParenStartColumn);
                } else if (!first && (ctx.column() + need > lineBudget || wouldExceedLineLength(need))) {
                    ctx.print(",");
                    ctx.println();
                    lambdaParamContinuationToColumn(openParenStartColumn);
                } else if (!first) {
                    ctx.print(", ");
                }
                ctx.accept(p, arg);
                first = false;
            }
        } else {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                ctx.println();
                lambdaParamContinuationToColumn(openParenStartColumn);
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(",");
                }
            }
        }
    }

    private void lambdaParamContinuationToColumn(int openParenStartColumn) {
        int target = openParenStartColumn + fmt.continuationIndentSize();
        ctx.padToColumn0(target);
    }

    /** Estimated width of type parameters if printed on one line with {@code ", "} separators. */
    int typeParametersFlatWidth(NodeList<TypeParameter> ps) {
        int w = 0;
        boolean first = true;
        for (TypeParameter p : ps) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += p.toString().length();
        }
        return w;
    }

    /** Whether a {@code <...>} type parameter list should wrap at the current column. */
    boolean typeParametersNeedWrap(NodeList<TypeParameter> ps) {
        if (isNullOrEmpty(ps)) {
            return false;
        }
        int width = ctx.column() + 1 + typeParametersFlatWidth(ps) + 1;
        return width > fmt.lineLength();
    }

    /** Prints {@code <T, U, ...>} type parameters with optional line wrapping. */
    void printTypeParameters(NodeList<TypeParameter> typeParameters, Void arg) {
        if (isNullOrEmpty(typeParameters)) {
            return;
        }
        if (!typeParametersNeedWrap(typeParameters)) {
            ctx.print("<");
            for (Iterator<TypeParameter> i = typeParameters.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            ctx.print(">");
            return;
        }
        ctx.print("<");
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int n = typeParameters.size();
            boolean first = true;
            for (int idx = 0; idx < n; idx++) {
                TypeParameter p = typeParameters.get(idx);
                int need = p.toString().length() + (first ? 0 : 2);
                boolean isLast = idx == n - 1;
                int lineBudget = fmt.lineLength();
                if (isLast) {
                    lineBudget += 1;
                }
                if (first && ctx.column() + need > lineBudget) {
                    ctx.println();
                    ctx.printRawContinuation();
                } else if (!first && (ctx.column() + need > lineBudget || wouldExceedLineLength(need))) {
                    ctx.print(",");
                    ctx.println();
                    ctx.printRawContinuation();
                } else if (!first) {
                    ctx.print(", ");
                }
                ctx.accept(p, arg);
                first = false;
            }
        } else {
            for (Iterator<TypeParameter> i = typeParameters.iterator(); i.hasNext(); ) {
                ctx.println();
                ctx.printRawContinuation();
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(",");
                }
            }
        }
        ctx.print(">");
    }

    /** Estimated width of type arguments if printed on one line with {@code ", "} separators. */
    int typeArgumentsFlatWidth(NodeList<Type> args) {
        int w = 0;
        boolean first = true;
        for (Type t : args) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    /** Whether a {@code <...>} type argument list on a reference type should wrap. */
    boolean typeArgumentsNeedWrap(NodeList<Type> args) {
        if (isNullOrEmpty(args)) {
            return false;
        }
        int width = ctx.column() + 1 + typeArgumentsFlatWidth(args) + 1;
        return width > fmt.lineLength();
    }
}
