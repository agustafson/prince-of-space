package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;

import java.util.Iterator;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/** Formats array initializer expressions using the same wrap vocabulary as other comma lists. */
@SuppressWarnings("VoidUsed")
final class ArrayInitializerFormatter {
    private final LayoutContext ctx;
    private final FormatterConfig fmt;
    private final ArgumentListFormatter argumentListFormatter;
    private final MethodChainFormatter methodChainFormatter;

    ArrayInitializerFormatter(
            LayoutContext ctx,
            ArgumentListFormatter argumentListFormatter,
            MethodChainFormatter methodChainFormatter) {
        this.ctx = ctx;
        this.fmt = ctx.config();
        this.argumentListFormatter = argumentListFormatter;
        this.methodChainFormatter = methodChainFormatter;
    }

    // R4: inline vs multi-line from flat width; R5: WIDE packs, BALANCED/NARROW one element per line;
    // R3: continuation before elements; optional trailing comma when multiline (FormatterConfig#trailingCommas).
    void format(ArrayInitializerExpr n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        int openBraceColumn = ctx.column();
        ctx.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            int arrayFlat = ctx.column() + methodChainFormatter.argsFlatWidth(n.getValues()) + 2;
            // Interior leading comments cannot fit inline: the default block-comment visitor emits a
            // trailing newline that breaks {a, /* sep */ b} layout and prevents convergence. Force
            // multi-line whenever any value carries a leading line/block comment.
            boolean hasInteriorComment = anyValueHasLeadingLineOrBlockComment(n.getValues());
            boolean multi = arrayFlat > fmt.lineLength() || hasInteriorComment;
            // Nested initializers align elements under the inner `{` column (openBraceColumn +
            // continuation indent); top-level arrays use normal continuation indent — clearer columns for 2D+ literals.
            boolean nestedArrayInitializer = n.getParentNode().map(parent -> parent instanceof ArrayInitializerExpr).orElse(false);
            if (multi) {
                if (fmt.wrapStyle() == WrapStyle.WIDE && !hasInteriorComment) {
                    // R5 wide: greedily pack until line full (same list policy as other wide lists).
                    argumentListFormatter.printGreedyCommaLines(n.getValues(), arg, 2, true, 0);
                    if (fmt.trailingCommas()) {
                        ctx.print(",");
                        ctx.println();
                    }
                    ctx.print("}");
                } else {
                    printTallInitializer(n, arg, openBraceColumn, nestedArrayInitializer);
                }
            } else {
                printInlineInitializer(n, arg);
                ctx.print("}");
            }
        } else {
            ctx.print("}");
        }
        ctx.printOrphanCommentsEnding(n);
    }

    private static boolean anyValueHasLeadingLineOrBlockComment(NodeList<Expression> values) {
        for (Expression v : values) {
            Optional<Comment> c = v.getComment();
            if (c.isPresent() && (c.get() instanceof LineComment || c.get() instanceof BlockComment)) {
                return true;
            }
        }
        return false;
    }

    private void printTallInitializer(
            ArrayInitializerExpr n,
            Void arg,
            int openBraceColumn,
            boolean alignToNestedArrayBrace) {
        int nestedElementColumn = openBraceColumn + fmt.continuationIndentSize();
        ctx.println();
        if (alignToNestedArrayBrace) {
            ctx.padToColumn0(nestedElementColumn);
        } else {
            ctx.printCont();
        }
        int elementColumn = ctx.column();
        for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
            Expression expr = i.next();
            printValueWithLeadingComment(expr, arg, elementColumn);
            if (i.hasNext()) {
                ctx.print(",");
                ctx.println();
                if (alignToNestedArrayBrace) {
                    ctx.padToColumn0(nestedElementColumn);
                } else {
                    ctx.printCont();
                }
            }
        }
        if (fmt.trailingCommas()) {
            ctx.print(",");
        }
        ctx.println();
        if (alignToNestedArrayBrace) {
            ctx.padToColumn0(openBraceColumn);
        }
        ctx.print("}");
    }

    /**
     * Emits a value at {@code elementColumn}, normalizing any leading line/block comment so the
     * default visitor's trailing newline cannot break the multi-line layout. Mutates the AST by
     * stripping the comment for the remainder of this pass; the convergence loop re-parses each
     * pass, so this does not leak across iterations.
     */
    private void printValueWithLeadingComment(Expression expr, Void arg, int elementColumn) {
        Optional<Comment> leading = expr.getComment();
        if (leading.isEmpty()) {
            ctx.accept(expr, arg);
            return;
        }
        Comment c = leading.get();
        if (c instanceof BlockComment bc) {
            ctx.printNormalizedBlockComment(bc, elementColumn);
            ctx.padToColumn0(elementColumn);
            expr.removeComment();
            ctx.accept(expr, arg);
        } else if (c instanceof LineComment lc) {
            ctx.print("//");
            String content = lc.getContent();
            if (!content.isEmpty() && !content.startsWith(" ")) {
                ctx.print(" ");
            }
            ctx.print(content);
            ctx.println();
            ctx.padToColumn0(elementColumn);
            expr.removeComment();
            ctx.accept(expr, arg);
        } else {
            ctx.accept(expr, arg);
        }
    }

    private void printInlineInitializer(ArrayInitializerExpr n, Void arg) {
        for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
            Expression expr = i.next();
            ctx.accept(expr, arg);
            if (i.hasNext()) {
                ctx.print(", ");
            }
        }
    }
}
