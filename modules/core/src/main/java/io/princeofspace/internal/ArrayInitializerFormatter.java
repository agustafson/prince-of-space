package io.princeofspace.internal;

import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;

import java.util.Iterator;

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
        ctx.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            int arrayFlat = ctx.column() + methodChainFormatter.argsFlatWidth(n.getValues()) + 2;
            boolean multi = arrayFlat > fmt.lineLength();
            if (multi) {
                if (fmt.wrapStyle() == WrapStyle.WIDE) {
                    // R5 wide: greedily pack until line full (same list policy as other wide lists).
                    argumentListFormatter.printGreedyCommaLines(n.getValues(), arg, 2, true, 0);
                    if (fmt.trailingCommas()) {
                        ctx.print(",");
                        ctx.println();
                    }
                    ctx.print("}");
                } else {
                    printTallInitializer(n, arg);
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

    private void printTallInitializer(ArrayInitializerExpr n, Void arg) {
        ctx.println();
        ctx.printCont();
        for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
            Expression expr = i.next();
            ctx.accept(expr, arg);
            if (i.hasNext()) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
            }
        }
        if (fmt.trailingCommas()) {
            ctx.print(",");
        }
        ctx.println();
        ctx.print("}");
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
