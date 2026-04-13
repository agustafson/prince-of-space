package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Width-aware pretty printer driven by {@link FormatterConfig}. Extends JavaParser's default visitor
 * and overrides layout for chains, wrapping, type clauses, try-with-resources, and array literals.
 */
final class PrincePrettyPrinterVisitor extends DefaultPrettyPrinterVisitor {

    private final FormatterConfig fmt;

    PrincePrettyPrinterVisitor(PrinterConfiguration configuration, FormatterConfig fmt) {
        super(configuration);
        this.fmt = fmt;
    }

    @Override
    protected void printMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        BodyDeclaration<?> prev = null;
        for (BodyDeclaration<?> member : members) {
            // Add blank line between members, EXCEPT between consecutive fields
            if (prev != null && !(prev instanceof FieldDeclaration && member instanceof FieldDeclaration)) {
                printer.println();
            }
            printer.println();
            member.accept(this, arg);
            prev = member;
        }
        printer.println();
    }

    @Override
    public void visit(BlockStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.println("{");
        if (n.getStatements() != null) {
            printer.indent();
            Statement prev = null;
            for (Statement s : n.getStatements()) {
                // Preserve blank lines between statements from original source
                if (prev != null && prev.getRange().isPresent() && s.getRange().isPresent()) {
                    int prevEnd = prev.getRange().get().end.line;
                    int curStart = s.getRange().get().begin.line;
                    if (curStart > prevEnd + 1) {
                        printer.println();
                    }
                }
                s.accept(this, arg);
                printer.println();
                prev = s;
            }
        }
        printOrphanCommentsEnding(n);
        printer.unindent();
        printer.print("}");
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("try ");
        if (!n.getResources().isEmpty()) {
            printer.print("(");
            int alignCol = column();
            Iterator<Expression> resources = n.getResources().iterator();
            boolean first = true;
            while (resources.hasNext()) {
                resources.next().accept(this, arg);
                if (resources.hasNext()) {
                    printer.print(";");
                    printer.println();
                    if (first) {
                        printer.indentWithAlignTo(alignCol);
                    }
                }
                first = false;
            }
            if (n.getResources().size() > 1) {
                printer.unindent();
            }
            if (fmt.closingParenOnNewLine() && n.getResources().size() > 1) {
                printer.println();
            }
            printer.print(") ");
        }
        n.getTryBlock().accept(this, arg);
        for (CatchClause c : n.getCatchClauses()) {
            c.accept(this, arg);
        }
        if (n.getFinallyBlock().isPresent()) {
            printer.print(" finally ");
            n.getFinallyBlock().get().accept(this, arg);
        }
    }

    private int column() {
        return printer.getCursor().column;
    }

    /**
     * Continuation indent: {@code continuationIndentSize} spaces, or that many tab characters when
     * using tabs (same convention as {@link io.princeofspace.model.FormatterConfig}: {@code indentSize}
     * is tabs per indent level in tab mode, not a pixel width).
     */
    private void printCont() {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            printer.print("\t".repeat(fmt.continuationIndentSize()));
        } else {
            printer.print(" ".repeat(fmt.continuationIndentSize()));
        }
    }

    /** Narrow style uses extra indentation for wrapped list items (implements, etc.). */
    private void printNarrowListIndent() {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            printer.print("\t".repeat(fmt.continuationIndentSize() * 2));
        } else {
            printer.print(" ".repeat(fmt.continuationIndentSize() * 2));
        }
    }

    private static int est(Expression e) {
        return e.toString().length();
    }

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

    private static Expression chainBase(MethodCallExpr outer) {
        Expression e = outer.getScope().orElseThrow();
        while (e instanceof MethodCallExpr mc) {
            e = mc.getScope().orElseThrow();
        }
        return e;
    }

    private int chainOneLineWidth(Expression base, List<MethodCallExpr> calls) {
        int w = est(base);
        for (MethodCallExpr mc : calls) {
            w += 1 + mc.getName().asString().length() + 2 + argsFlatWidth(mc.getArguments()) + 1;
        }
        return w;
    }

    private static int argsFlatWidth(NodeList<? extends Expression> args) {
        int w = 0;
        boolean first = true;
        for (Expression a : args) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += est(a);
        }
        return w;
    }

    private void printArgumentsInline(NodeList<? extends Expression> args) {
        printer.print("(");
        for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
            printer.print(i.next().toString());
            if (i.hasNext()) {
                printer.print(", ");
            }
        }
        printer.print(")");
    }

    private static int paramsFlatWidth(NodeList<Parameter> ps) {
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

    private static int typeParametersFlatWidth(NodeList<TypeParameter> ps) {
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

    private boolean typeParametersNeedWrap(NodeList<TypeParameter> ps) {
        if (isNullOrEmpty(ps)) {
            return false;
        }
        int width = column() + 1 + typeParametersFlatWidth(ps) + 1;
        return width > fmt.preferredLineLength() || width > fmt.maxLineLength();
    }

    @Override
    protected void printTypeParameters(NodeList<TypeParameter> typeParameters, Void arg) {
        if (isNullOrEmpty(typeParameters)) {
            return;
        }
        if (!typeParametersNeedWrap(typeParameters)) {
            printer.print("<");
            for (Iterator<TypeParameter> i = typeParameters.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(">");
            return;
        }
        printer.print("<");
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int n = typeParameters.size();
            boolean first = true;
            for (int idx = 0; idx < n; idx++) {
                TypeParameter p = typeParameters.get(idx);
                int need = p.toString().length() + (first ? 0 : 2);
                boolean isLast = idx == n - 1;
                int lineBudget = fmt.preferredLineLength();
                if (isLast) {
                    lineBudget += 1;
                }
                if (first && column() + need > lineBudget) {
                    printer.println();
                    printCont();
                } else if (!first && (column() + need > lineBudget || wouldExceedMaxLine(need))) {
                    printer.print(",");
                    printer.println();
                    printCont();
                } else if (!first) {
                    printer.print(", ");
                }
                p.accept(this, arg);
                first = false;
            }
        } else {
            for (Iterator<TypeParameter> i = typeParameters.iterator(); i.hasNext(); ) {
                printer.println();
                printCont();
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(",");
                }
            }
        }
        printer.print(">");
    }

    private NodeList<AnnotationExpr> declarationAnnotations(MethodDeclaration n) {
        if (n.getModifiers().isEmpty()) {
            return new NodeList<>(n.getAnnotations());
        }
        int firstModifierColumn = n.getModifiers().get(0).getRange().map(r -> r.begin.column).orElse(Integer.MAX_VALUE);
        NodeList<AnnotationExpr> result = new NodeList<>();
        for (AnnotationExpr annotation : n.getAnnotations()) {
            int annotationColumn = annotation.getRange().map(r -> r.begin.column).orElse(Integer.MIN_VALUE);
            if (annotationColumn < firstModifierColumn) {
                result.add(annotation);
            }
        }
        return result;
    }

    private NodeList<AnnotationExpr> inlineReturnTypeAnnotations(MethodDeclaration n) {
        if (n.getModifiers().isEmpty()) {
            return new NodeList<>();
        }
        int lastModifierColumn = n.getModifiers().get(n.getModifiers().size() - 1)
                .getRange()
                .map(r -> r.end.column)
                .orElse(Integer.MAX_VALUE);
        NodeList<AnnotationExpr> result = new NodeList<>();
        for (AnnotationExpr annotation : n.getAnnotations()) {
            int annotationColumn = annotation.getRange().map(r -> r.begin.column).orElse(Integer.MIN_VALUE);
            if (annotationColumn > lastModifierColumn) {
                result.add(annotation);
            }
        }
        return result;
    }

    private boolean mustWrapChain(Expression base, List<MethodCallExpr> calls) {
        return column() + chainOneLineWidth(base, calls) > fmt.preferredLineLength();
    }

    private boolean mustHardWrapChain(Expression base, List<MethodCallExpr> calls) {
        return column() + chainOneLineWidth(base, calls) > fmt.maxLineLength();
    }

    private boolean shouldWrapLambdaHeavyChain(Expression base, List<MethodCallExpr> calls) {
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

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        if (n.getScope().isEmpty()) {
            super.visit(n, arg);
            return;
        }
        MethodCallExpr outer = outermostCall(n);
        if (!outer.equals(n)) {
            return;
        }
        List<MethodCallExpr> calls = chainInOrder(outer);
        Expression base = chainBase(outer);
        if (base instanceof TextBlockLiteralExpr) {
            printChainInlineWithInlineArgs(base, calls, arg);
            return;
        }
        boolean wrap = mustHardWrapChain(base, calls)
                || mustWrapChain(base, calls)
                || shouldWrapLambdaHeavyChain(base, calls);
        if (!wrap) {
            printChainInline(base, calls, arg);
            return;
        }
        // All wrap styles format chains the same way: one call per line
        printChainBalancedOrNarrow(base, calls, arg);
    }

    private void printChainInline(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        for (MethodCallExpr mc : calls) {
            printer.print(".");
            printTypeArgs(mc, arg);
            mc.getName().accept(this, arg);
            printArguments(mc.getArguments(), arg);
        }
    }

    private void printChainInlineWithInlineArgs(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        for (MethodCallExpr mc : calls) {
            printer.print(".");
            printTypeArgs(mc, arg);
            mc.getName().accept(this, arg);
            printArgumentsInline(mc.getArguments());
        }
    }

    /**
     * Wrapped chains: one {@code .method()} per continuation line (leading-dot style), matching common
     * Kotlin / Prettier habits and keeping diffs one segment per line. Exception: a <strong>single</strong>
     * chained call with a {@link #isSimpleBase simple} receiver stays {@code Receiver.method(...)} on one
     * line so trivial {@code items.stream()} does not become two lines.
     */
    private void printChainBalancedOrNarrow(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        int lineStartColumn = column() - est(base);

        if (calls.size() == 1 && isSimpleBase(base)) {
            MethodCallExpr only = calls.get(0);
            printer.print(".");
            printTypeArgs(only, arg);
            only.getName().accept(this, arg);
            if (hasBlockLambdaArgument(only.getArguments())) {
                printer.indentWithAlignTo(lineStartColumn + fmt.continuationIndentSize());
                try {
                    printArguments(only.getArguments(), arg);
                } finally {
                    printer.unindent();
                }
            } else {
                printArguments(only.getArguments(), arg);
            }
            return;
        }

        printer.println();
        printCont();
        int contCol = column();
        printer.indentWithAlignTo(contCol);
        for (int i = 0; i < calls.size(); i++) {
            MethodCallExpr mc = calls.get(i);
            if (i > 0) {
                printer.println();
            }
            printer.print(".");
            printTypeArgs(mc, arg);
            mc.getName().accept(this, arg);
            if (hasBlockLambdaArgument(mc.getArguments())) {
                printer.indentWithAlignTo(lineStartColumn + fmt.continuationIndentSize());
                try {
                    printArguments(mc.getArguments(), arg);
                } finally {
                    printer.unindent();
                }
            } else {
                printArguments(mc.getArguments(), arg);
            }
        }
        printer.unindent();
    }

    private static boolean isSimpleBase(Expression base) {
        return base instanceof com.github.javaparser.ast.expr.NameExpr
                || base instanceof com.github.javaparser.ast.expr.FieldAccessExpr
                || base instanceof com.github.javaparser.ast.expr.ThisExpr
                || base instanceof com.github.javaparser.ast.expr.SuperExpr;
    }

    private static boolean hasBlockLambdaArgument(NodeList<? extends Expression> args) {
        for (Expression expression : args) {
            if (expression instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldGlueWrappedClosingParen(NodeList<? extends Expression> args) {
        if (args.isEmpty()) {
            return false;
        }
        if (hasBlockLambdaArgument(args)) {
            return true;
        }
        Expression last = args.get(args.size() - 1);
        return args.size() == 1 && last instanceof MethodCallExpr;
    }

    private static void collectSameOp(BinaryExpr.Operator op, Expression e, List<Expression> out) {
        if (e instanceof BinaryExpr b && b.getOperator() == op) {
            collectSameOp(op, b.getLeft(), out);
            collectSameOp(op, b.getRight(), out);
        } else {
            out.add(e);
        }
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
            List<Expression> parts = new ArrayList<>();
            collectSameOp(n.getOperator(), (Expression) n, parts);
            int flat = column();
            for (Expression p : parts) {
                flat += est(p) + 4;
            }
            if (flat <= fmt.preferredLineLength() && flat <= fmt.maxLineLength()) {
                parts.get(0).accept(this, arg);
                String os = n.getOperator().asString();
                for (int i = 1; i < parts.size(); i++) {
                    printer.print(" ");
                    printer.print(os);
                    printer.print(" ");
                    parts.get(i).accept(this, arg);
                }
                return;
            }
            String os = n.getOperator().asString();
            if (fmt.wrapStyle() == WrapStyle.BALANCED || fmt.wrapStyle() == WrapStyle.NARROW) {
                // One operand per continuation line (operator at line start); NARROW matches this for &&/||
                parts.get(0).accept(this, arg);
                for (int i = 1; i < parts.size(); i++) {
                    printer.println();
                    printCont();
                    printer.print(os);
                    printer.print(" ");
                    parts.get(i).accept(this, arg);
                }
            } else {
                // WIDE: greedy packing (continuation indent affects only column position, not line budget)
                printBinaryGreedy(parts, os, arg);
            }
            return;
        }
        if (n.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Expression> parts = new ArrayList<>();
            collectSameOp(BinaryExpr.Operator.PLUS, (Expression) n, parts);
            int flat = column();
            for (Expression p : parts) {
                flat += est(p) + 3;
            }
            if (flat <= fmt.preferredLineLength() && flat <= fmt.maxLineLength()) {
                parts.get(0).accept(this, arg);
                for (int i = 1; i < parts.size(); i++) {
                    printer.print(" + ");
                    parts.get(i).accept(this, arg);
                }
                return;
            }
            if (fmt.wrapStyle() == WrapStyle.NARROW) {
                parts.get(0).accept(this, arg);
                for (int i = 1; i < parts.size(); i++) {
                    printer.println();
                    printCont();
                    printer.print("+ ");
                    parts.get(i).accept(this, arg);
                }
            } else {
                printBinaryGreedy(parts, "+", arg);
            }
            return;
        }
        super.visit(n, arg);
    }

    /** Greedy packing for binary operator chains: pack as many operands per line as fit. */
    private void printBinaryGreedy(List<Expression> parts, String op, Void arg) {
        printBinaryGreedy(parts, op, arg, fmt.preferredLineLength());
    }

    private void printBinaryGreedy(List<Expression> parts, String op, Void arg, int budget) {
        parts.get(0).accept(this, arg);
        int used = column();
        for (int i = 1; i < parts.size(); i++) {
            int opLen = op.length() + 2; // " op "
            int partLen = est(parts.get(i));
            boolean overPreferred = used + opLen + partLen > budget;
            boolean overMax = used + opLen + partLen > fmt.maxLineLength();
            if (overPreferred || overMax) {
                printer.println();
                printCont();
                printer.print(op);
                printer.print(" ");
                used = column();
            } else {
                printer.print(" ");
                printer.print(op);
                printer.print(" ");
                used += opLen;
            }
            parts.get(i).accept(this, arg);
            used = column();
        }
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        int flat = column() + est(n.getCondition()) + 3 + est(n.getThenExpr()) + 3 + est(n.getElseExpr());
        if (flat <= fmt.preferredLineLength() && flat <= fmt.maxLineLength()) {
            n.getCondition().accept(this, arg);
            printer.print(" ? ");
            n.getThenExpr().accept(this, arg);
            printer.print(" : ");
            n.getElseExpr().accept(this, arg);
            return;
        }
        n.getCondition().accept(this, arg);
        printer.println();
        printCont();
        printer.print("? ");
        n.getThenExpr().accept(this, arg);
        printer.println();
        printCont();
        printer.print(": ");
        n.getElseExpr().accept(this, arg);
    }

    @Override
    protected <T extends Expression> void printArguments(NodeList<T> args, Void arg) {
        printer.print("(");
        boolean wrapped = false;
        if (!isNullOrEmpty(args)) {
            wrapped = argsNeedWrap(args);
            printCommaSeparatedExprs(args, arg);
        }
        if (fmt.closingParenOnNewLine() && wrapped && !shouldGlueWrappedClosingParen(args)) {
            printer.println();
        }
        printer.print(")");
    }

    private boolean argsNeedWrap(NodeList<? extends Expression> args) {
        int width = column() + 1 + argsFlatWidth(args);
        return width > fmt.preferredLineLength() || width > fmt.maxLineLength();
    }

    private void printCommaSeparatedExprs(NodeList<? extends Expression> args, Void arg) {
        if (!argsNeedWrap(args)) {
            for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            return;
        }
        if (args.size() == 1) {
            args.get(0).accept(this, arg);
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            int extraLastLine =
                    fmt.closingParenOnNewLine() ? 2 : 0; // ")" on its own line — no width reserved on last arg line
            printGreedyCommaLines(args, arg, 0, false, extraLastLine);
        } else {
            for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                printer.println();
                printCont();
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(",");
                }
            }
        }
    }

    /**
     * Greedy comma-separated expressions. Uses {@link #column()} after continuation indent so
     * {@code continuationIndentSize} does not change wrap positions vs other configs (only indent).
     *
     * @param extraLastLineBudget when the closing delimiter is printed on its own line, extra width
     *     allowed on the last content line (no need to reserve for {@code )} on that line).
     */
    private void printGreedyCommaLines(
            NodeList<? extends Expression> args,
            Void arg,
            int trailingWidth,
            boolean avoidLoneLastItem,
            int extraLastLineBudget) {
        boolean first = true;
        int budget = fmt.preferredLineLength() - trailingWidth;
        Iterator<? extends Expression> iter = args.iterator();
        int remaining = args.size();
        while (iter.hasNext()) {
            Expression e = iter.next();
            int need = est(e) + (first ? 0 : 2);
            boolean shouldWrapForLoneLastItem = avoidLoneLastItem && !first && remaining == 2;
            int lineBudget = budget + (extraLastLineBudget > 0 && remaining == 1 ? extraLastLineBudget : 0);
            if (!first
                    && (column() + need > lineBudget
                            || wouldExceedMaxLine(need)
                            || shouldWrapForLoneLastItem)) {
                printer.print(",");
                printer.println();
                printCont();
            } else if (!first) {
                printer.print(", ");
            }
            e.accept(this, arg);
            first = false;
            remaining--;
        }
    }

    private boolean paramsNeedWrap(NodeList<Parameter> ps) {
        int width = column() + 1 + paramsFlatWidth(ps);
        return width > fmt.preferredLineLength() || width > fmt.maxLineLength();
    }

    /** Whether appending {@code additionalWidth} characters on the current line would exceed the configured hard line limit. */
    private boolean wouldExceedMaxLine(int additionalWidth) {
        return column() + additionalWidth > fmt.maxLineLength();
    }

    private void printParametersList(NodeList<Parameter> ps, Void arg) {
        if (isNullOrEmpty(ps)) {
            return;
        }
        if (!paramsNeedWrap(ps)) {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
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
                int lineBudget = fmt.preferredLineLength();
                if (isLast) {
                    // When ")" stays on the last param line, reserve ") {" / ");"; when ")" is alone on
                    // the next line, that width is not needed on the param line.
                    lineBudget += fmt.closingParenOnNewLine() ? 3 : -3;
                }
                if (first && (column() + need > lineBudget || wouldExceedMaxLine(need))) {
                    printer.println();
                    printCont();
                } else if (!first && (column() + need > lineBudget || wouldExceedMaxLine(need))) {
                    printer.print(",");
                    printer.println();
                    printCont();
                } else if (!first) {
                    printer.print(", ");
                }
                p.accept(this, arg);
                first = false;
            }
        } else {
            for (Iterator<Parameter> i = ps.iterator(); i.hasNext(); ) {
                printer.println();
                printCont();
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(",");
                }
            }
        }
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        printTypeParameters(n.getTypeParameters(), arg);
        if (n.isGeneric()) {
            printer.print(" ");
        }
        n.getName().accept(this, arg);
        printer.print("(");
        n.getReceiverParameter()
                .ifPresent(
                        rp -> {
                            rp.accept(this, arg);
                            if (!isNullOrEmpty(n.getParameters())) {
                                printer.print(", ");
                            }
                        });
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters());
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            printer.println();
        }
        printer.print(")");
        if (!isNullOrEmpty(n.getThrownExceptions())) {
            printer.print(" throws ");
            for (Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
                ReferenceType name = i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(" ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        NodeList<AnnotationExpr> declarationAnnotations = declarationAnnotations(n);
        NodeList<AnnotationExpr> inlineReturnTypeAnnotations = inlineReturnTypeAnnotations(n);
        printMemberAnnotations(declarationAnnotations, arg);
        printModifiers(n.getModifiers());
        printTypeParameters(n.getTypeParameters(), arg);
        if (!isNullOrEmpty(n.getTypeParameters())) {
            printer.print(" ");
        }
        if (!inlineReturnTypeAnnotations.isEmpty()) {
            for (Iterator<AnnotationExpr> i = inlineReturnTypeAnnotations.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                printer.print(" ");
            }
        }
        n.getType().accept(this, arg);
        printer.print(" ");
        n.getName().accept(this, arg);
        printer.print("(");
        n.getReceiverParameter()
                .ifPresent(
                        rp -> {
                            rp.accept(this, arg);
                            if (!isNullOrEmpty(n.getParameters())) {
                                printer.print(", ");
                            }
                        });
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters());
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            printer.println();
        }
        printer.print(")");
        if (!isNullOrEmpty(n.getThrownExceptions())) {
            printer.print(" throws ");
            for (Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
                ReferenceType name = i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        if (!n.getBody().isPresent()) {
            printer.print(";");
        } else {
            BlockStmt body = n.getBody().get();
            boolean modernCompactEmptyMethod =
                    fmt.javaLanguageLevel() != com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_8
                            && body.getStatements().isEmpty()
                            && body.getComment().isEmpty()
                            && body.getOrphanComments().isEmpty();
            if (modernCompactEmptyMethod) {
                printer.print(" {}");
            } else {
                printer.print(" ");
                body.accept(this, arg);
            }
        }
    }

    @Override
    public void visit(com.github.javaparser.ast.body.RecordDeclaration n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        printer.print("record ");
        n.getName().accept(this, arg);
        printTypeParameters(n.getTypeParameters(), arg);
        printer.print("(");
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters());
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            printer.println();
        }
        printer.print(")");
        if (!n.getImplementedTypes().isEmpty()) {
            printer.print(" implements ");
            for (Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
                ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        if (isNullOrEmpty(n.getMembers())) {
            printer.print(" {}");
            return;
        }
        printer.println(" {");
        printer.indent();
        if (!isNullOrEmpty(n.getMembers())) {
            printMembers(n.getMembers(), arg);
        }
        printOrphanCommentsEnding(n);
        printer.unindent();
        printer.print("}");
    }

    @Override
    public void visit(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        if (!n.isCompact()) {
            printMemberAnnotations(n.getAnnotations(), arg);
            printModifiers(n.getModifiers());
            if (n.isInterface()) {
                printer.print("interface ");
            } else {
                printer.print("class ");
            }
            n.getName().accept(this, arg);
            printTypeParameters(n.getTypeParameters(), arg);
            boolean typeClauseWrapped = false;
            if (!n.getExtendedTypes().isEmpty()) {
                printer.print(" extends ");
                for (Iterator<ClassOrInterfaceType> i = n.getExtendedTypes().iterator(); i.hasNext(); ) {
                    ClassOrInterfaceType c = i.next();
                    c.accept(this, arg);
                    if (i.hasNext()) {
                        printer.print(", ");
                    }
                }
            }
            if (!n.getImplementedTypes().isEmpty()) {
                typeClauseWrapped = printImplementsClause(n.getImplementedTypes(), arg);
            }
            if (!n.getPermittedTypes().isEmpty()) {
                typeClauseWrapped = printPermitsClause(n.getPermittedTypes(), arg) || typeClauseWrapped;
            }
            if (typeClauseWrapped && fmt.closingParenOnNewLine()) {
                printer.println();
                printer.println("{");
            } else {
                printer.println(" {");
            }
            printer.indent();
        }
        if (!isNullOrEmpty(n.getMembers())) {
            if (n.isCompact()) {
                printCompactClassMembers(n.getMembers(), arg);
            } else {
                printMembers(n.getMembers(), arg);
            }
        }
        printOrphanCommentsEnding(n);
        if (!n.isCompact()) {
            printer.unindent();
            printer.print("}");
        }
    }

    private int implementsTypesWidth(NodeList<ClassOrInterfaceType> types) {
        int w = 0;
        boolean first = true;
        for (ClassOrInterfaceType t : types) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    /** @return true if the clause wrapped to a new line. */
    private boolean printImplementsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        int header = column();
        // Check if everything fits on the current line (include " {" trailing)
        int inlineWidth = header + 12 + implementsTypesWidth(types) + 2;
        if (inlineWidth <= fmt.preferredLineLength() && inlineWidth <= fmt.maxLineLength()) {
            printer.print(" implements ");
            printTypeListInline(types, arg);
            return false;
        }
        // Wrapping needed
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printer.println();
            printCont();
            printer.print("implements ");
            printTypeListGreedy(types, arg);
            return true;
        }
        if (fmt.wrapStyle() == WrapStyle.BALANCED) {
            printer.println();
            printCont();
            printer.print("implements ");
            types.get(0).accept(this, arg);
            for (int i = 1; i < types.size(); i++) {
                printer.print(",");
                printer.println();
                printCont();
                types.get(i).accept(this, arg);
            }
            return true;
        }
        // NARROW: implements keyword alone, types double-indented
        printer.println();
        printCont();
        printer.print("implements");
        for (int i = 0; i < types.size(); i++) {
            printer.println();
            printNarrowListIndent();
            types.get(i).accept(this, arg);
            if (i < types.size() - 1) {
                printer.print(",");
            }
        }
        return true;
    }

    /** @return true if the clause wrapped to a new line. */
    private boolean printPermitsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.isEmpty()) {
            return false;
        }
        int header = column();
        int permitsInline = header + 9 + implementsTypesWidth(types);
        if (fmt.wrapStyle() != WrapStyle.NARROW
                && permitsInline <= fmt.preferredLineLength()
                && permitsInline <= fmt.maxLineLength()) {
            printer.print(" permits ");
            printTypeListInline(types, arg);
            return false;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printer.println();
            printCont();
            printer.print("permits ");
            printTypeListGreedy(types, arg);
            return true;
        }
        printer.println();
        printCont();
        printer.print("permits ");
        types.get(0).accept(this, arg);
        for (int i = 1; i < types.size(); i++) {
            printer.print(",");
            printer.println();
            if (fmt.wrapStyle() == WrapStyle.BALANCED) {
                printCont();
            } else {
                printNarrowListIndent();
            }
            types.get(i).accept(this, arg);
        }
        return true;
    }

    private void printTypeListGreedy(NodeList<ClassOrInterfaceType> types, Void arg) {
        int budget = fmt.preferredLineLength() - 2;
        boolean first = true;
        for (ClassOrInterfaceType t : types) {
            int need = t.toString().length() + (first ? 0 : 2);
            if (!first && (column() + need > budget || wouldExceedMaxLine(need))) {
                printer.print(",");
                printer.println();
                printCont();
            } else if (!first) {
                printer.print(", ");
            }
            t.accept(this, arg);
            first = false;
        }
    }

    private void printTypeListInline(NodeList<ClassOrInterfaceType> types, Void arg) {
        for (Iterator<ClassOrInterfaceType> i = types.iterator(); i.hasNext(); ) {
            i.next().accept(this, arg);
            if (i.hasNext()) {
                printer.print(", ");
            }
        }
    }

    @Override
    public void visit(EnumDeclaration n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        printer.print("enum ");
        n.getName().accept(this, arg);
        if (!n.getImplementedTypes().isEmpty()) {
            printer.print(" implements ");
            for (Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
                ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        // Check if simple enum (no bodies, fits on one line)
        boolean hasMembers = !n.getMembers().isEmpty();
        boolean hasBodies = n.getEntries().stream().anyMatch(e -> !e.getClassBody().isEmpty());
        int flatWidth = enumConstantsFlatWidth(n.getEntries());
        int oneLineEnum = column() + 3 + flatWidth + 2;
        boolean fitsOneLine = oneLineEnum <= fmt.preferredLineLength()
                && oneLineEnum <= fmt.maxLineLength()
                && !hasBodies && !hasMembers;
        if (fitsOneLine && n.getEntries().isNonEmpty()) {
            printer.print(" { ");
            for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(" }");
            return;
        }
        printer.println(" {");
        printer.indent();
        if (n.getEntries().isNonEmpty()) {
            if (fmt.wrapStyle() == WrapStyle.WIDE && !hasBodies) {
                // Greedy packing of constants
                boolean first = true;
                int budget = fmt.preferredLineLength();
                for (EnumConstantDeclaration e : n.getEntries()) {
                    int need = e.toString().length() + (first ? 0 : 2);
                    if (!first && (column() + need > budget || wouldExceedMaxLine(need))) {
                        printer.print(",");
                        printer.println();
                    } else if (!first) {
                        printer.print(", ");
                    }
                    e.accept(this, arg);
                    first = false;
                }
                if (fmt.trailingCommas() && !n.getEntries().isEmpty()) {
                    printer.print(",");
                }
            } else {
                // One per line
                for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
                    i.next().accept(this, arg);
                    if (i.hasNext()) {
                        printer.println(",");
                    } else if (fmt.trailingCommas()) {
                        printer.print(",");
                    }
                }
            }
        }
        if (hasMembers) {
            printer.println(";");
            printMembers(n.getMembers(), arg);
        } else if (n.getEntries().isNonEmpty()) {
            printer.println();
        }
        printer.unindent();
        printer.print("}");
    }

    private static int enumConstantsFlatWidth(NodeList<EnumConstantDeclaration> entries) {
        int w = 0;
        boolean first = true;
        for (EnumConstantDeclaration e : entries) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += e.toString().length();
        }
        return w;
    }

    @Override
    public void visit(com.github.javaparser.ast.expr.ArrayInitializerExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            int arrayFlat = column() + argsFlatWidth(n.getValues()) + 2;
            boolean multi =
                    arrayFlat > fmt.preferredLineLength() || arrayFlat > fmt.maxLineLength();
            if (multi) {
                if (fmt.wrapStyle() == WrapStyle.WIDE) {
                    // Greedy inline packing
                    printGreedyCommaLines(n.getValues(), arg, 2, true, 0);
                    printer.print("}");
                } else {
                    // One per line
                    printer.println();
                    printCont();
                    for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
                        Expression expr = i.next();
                        expr.accept(this, arg);
                        if (i.hasNext()) {
                            printer.print(",");
                            printer.println();
                            printCont();
                        }
                    }
                    if (fmt.trailingCommas()) {
                        printer.print(",");
                    }
                    printer.println();
                    printer.print("}");
                }
            } else {
                for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
                    Expression expr = i.next();
                    expr.accept(this, arg);
                    if (i.hasNext()) {
                        printer.print(", ");
                    }
                }
                printer.print("}");
            }
        } else {
            printer.print("}");
        }
        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(TextBlockLiteralExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("\"\"\"\n");
        printer.print(n.getValue());
        printer.print("\"\"\"");
        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(SwitchExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("switch (");
        n.getSelector().accept(this, arg);
        printer.println(") {");
        printer.indent();
        for (SwitchEntry entry : n.getEntries()) {
            printer.println();
            printSwitchEntry(entry, arg);
        }
        printer.println();
        printer.unindent();
        printer.print("}");
        printOrphanCommentsEnding(n);
    }

    private void printSwitchEntry(SwitchEntry entry, Void arg) {
        if (entry.getLabels().isEmpty()) {
            printer.print("default");
        } else {
            printer.print("case ");
            for (Iterator<Expression> i = entry.getLabels().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        entry.getGuard().ifPresent(
                guard -> {
                    printer.print(" when ");
                    guard.accept(this, arg);
                });
        if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
            printer.print(":");
            return;
        }
        printer.print(" -> ");
        if (entry.getStatements().size() == 1) {
            entry.getStatements().get(0).accept(this, arg);
            return;
        }
        for (Iterator<Statement> i = entry.getStatements().iterator(); i.hasNext(); ) {
            i.next().accept(this, arg);
            if (i.hasNext()) {
                printer.print(" ");
            }
        }
    }

}
