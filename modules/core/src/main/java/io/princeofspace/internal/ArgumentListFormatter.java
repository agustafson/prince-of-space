package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
     *
     * <p>Width budget includes one column for {@code (}; the closing {@code )} is not reserved here — wrap
     * heuristics assume the callee accounts for {@code )} when laying out the inside of the list (see also
     * {@link #CLOSING_PAREN_INLINE_RESERVED_WIDTH} on wrapped layouts). Angle-bracket predicates reserve both
     * delimiters; see {@link #typeParametersNeedWrap}.
     */
    boolean argsNeedWrap(NodeList<? extends Expression> args) {
        int width = ctx.column() + 1 + methodChainFormatter.argsFlatWidth(args);
        return width > fmt.lineLength();
    }

    /**
     * R5 / R8 / TDR-021: when a wrapped call's last argument is a lambda — block- or expression-bodied —
     * palantir-java-format / Prettier / ktlint keep the lambda header (e.g. {@code () -> '{'} or
     * {@code s ->}) on the call line and let the lambda body wrap (block body via its own block indent;
     * expression body via the receiver chain or other inner wrap mechanic). The closing {@code )}
     * follows the lambda body inline, never on its own line, regardless of {@code closingParenOnNewLine}.
     * Returns {@code true} when:
     * <ul>
     *   <li>the args list is non-empty,</li>
     *   <li>the last arg is a {@code LambdaExpr} (any body),</li>
     *   <li>no preceding arg is itself a block-bodied lambda (multiple block lambdas read worse inline), and</li>
     *   <li>no preceding arg has comments that need to drive a line break.</li>
     * </ul>
     * Soft line-length overflow on the call line is accepted as a deliberate tradeoff — the
     * alternative (lambda header on its own line) reads worse for the trailing-lambda idiom.
     */
    boolean shouldUseTrailingLambdaLayout(NodeList<? extends Expression> args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        Expression last = args.get(args.size() - 1);
        if (!(last instanceof LambdaExpr)) {
            return false;
        }
        for (int i = 0; i < args.size() - 1; i++) {
            Expression e = args.get(i);
            if (e instanceof LambdaExpr leadingLambda && leadingLambda.getBody() instanceof BlockStmt) {
                return false;
            }
            if (commentUtils.hasLineOrBlockComment(e) || commentUtils.hasLeadingLineOrBlockComment(e)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Prints the leading arguments inline with {@code ", "} separators, then prints the trailing
     * lambda inline (no leading newline). For block-bodied lambdas the lambda visitor emits its own
     * multi-line body and a {@code }} at the call-line indent; for expression-bodied lambdas any inner
     * wrap (e.g. method-chain receiver) is driven by the body itself. In both cases the caller places
     * {@code )} immediately after the lambda body without inserting another newline.
     */
    void printTrailingLambdaLayout(NodeList<? extends Expression> args, Void arg) {
        int last = args.size() - 1;
        for (int i = 0; i < last; i++) {
            ctx.accept(args.get(i), arg);
            ctx.print(", ");
        }
        ctx.accept(args.get(last), arg);
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
            Expression only = args.get(0);
            if (shouldBreakBeforeSingleWrappedArg(only)) {
                ctx.println();
                ctx.printCont();
            }
            ctx.accept(only, arg);
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

    boolean shouldBreakBeforeSingleWrappedArg(Expression expression) {
        if (expression instanceof MethodCallExpr methodCall && methodCall.getScope().isPresent()) {
            return false;
        }
        return !(expression instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt);
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
            w += WidthMeasurer.flatWidth(p, fmt);
        }
        return w;
    }

    /**
     * Whether formal parameters need to wrap at the opening {@code (}.
     *
     * <p>Budget is {@code column + '(' + flatWidths}; closing {@code )} is not folded into this predicate (same
     * convention as {@link #argsNeedWrap}).
     */
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
                int need = WidthMeasurer.flatWidth(p, fmt) + (first ? 0 : 2);
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
                int need = WidthMeasurer.flatWidth(p, fmt) + (first ? 0 : 2);
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
            w += WidthMeasurer.flatWidth(p, fmt);
        }
        return w;
    }

    /**
     * Whether a {@code <...>} type parameter list should wrap at the current column.
     *
     * <p>Budget reserves both angle brackets: {@code column + '<' + content + '>'}.
     */
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
                int need = WidthMeasurer.flatWidth(p, fmt) + (first ? 0 : 2);
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
            w += WidthMeasurer.flatWidth(t, fmt);
        }
        return w;
    }

    /**
     * Whether a {@code <...>} type argument list on a reference type should wrap.
     *
     * <p>Budget reserves both angle brackets (see {@link #typeParametersNeedWrap}).
     */
    boolean typeArgumentsNeedWrap(NodeList<Type> args) {
        if (isNullOrEmpty(args)) {
            return false;
        }
        int width = ctx.column() + 1 + typeArgumentsFlatWidth(args) + 1;
        return width > fmt.lineLength();
    }
}
