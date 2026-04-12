package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
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

    private int column() {
        return Math.max(0, printer.getCursor().column - 1);
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
            printer.print("\t".repeat(fmt.indentSize() + fmt.continuationIndentSize()));
        } else {
            printer.print(" ".repeat(fmt.indentSize() + fmt.continuationIndentSize()));
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

    private boolean mustWrapChain(Expression base, List<MethodCallExpr> calls) {
        return column() + chainOneLineWidth(base, calls) > fmt.preferredLineLength();
    }

    private boolean mustHardWrapChain(Expression base, List<MethodCallExpr> calls) {
        return column() + chainOneLineWidth(base, calls) > fmt.maxLineLength();
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
        boolean wrap = mustHardWrapChain(base, calls) || mustWrapChain(base, calls);
        if (!wrap) {
            printChainInline(base, calls, arg);
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printChainWide(base, calls, arg);
        } else {
            printChainBalancedOrNarrow(base, calls, arg);
        }
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

    private void printChainBalancedOrNarrow(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        for (MethodCallExpr mc : calls) {
            printer.println();
            printCont();
            printer.print(".");
            printTypeArgs(mc, arg);
            mc.getName().accept(this, arg);
            printArguments(mc.getArguments(), arg);
        }
    }

    /** Greedy packing: start a new line before {@code .} when the segment would exceed preferred width. */
    private void printChainWide(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        for (MethodCallExpr mc : calls) {
            int seg = 1 + mc.getName().asString().length() + 2 + argsFlatWidth(mc.getArguments()) + 1;
            if (column() + seg > fmt.preferredLineLength()) {
                printer.println();
                printCont();
            }
            printer.print(".");
            printTypeArgs(mc, arg);
            mc.getName().accept(this, arg);
            printArguments(mc.getArguments(), arg);
        }
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
            if (flat <= fmt.preferredLineLength()) {
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
            parts.get(0).accept(this, arg);
            String os = n.getOperator().asString();
            for (int i = 1; i < parts.size(); i++) {
                printer.println();
                printCont();
                printer.print(os);
                printer.print(" ");
                parts.get(i).accept(this, arg);
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
            if (flat <= fmt.preferredLineLength()) {
                parts.get(0).accept(this, arg);
                for (int i = 1; i < parts.size(); i++) {
                    printer.print(" + ");
                    parts.get(i).accept(this, arg);
                }
                return;
            }
            parts.get(0).accept(this, arg);
            for (int i = 1; i < parts.size(); i++) {
                printer.println();
                printCont();
                printer.print("+ ");
                parts.get(i).accept(this, arg);
            }
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        int flat = column() + est(n.getCondition()) + 3 + est(n.getThenExpr()) + 3 + est(n.getElseExpr());
        if (flat <= fmt.preferredLineLength()) {
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
        if (!isNullOrEmpty(args)) {
            printCommaSeparatedExprs(args, arg);
        }
        if (fmt.closingParenOnNewLine() && !isNullOrEmpty(args) && argsNeedWrap(args)) {
            printer.println();
            printCont();
        }
        printer.print(")");
    }

    private boolean argsNeedWrap(NodeList<? extends Expression> args) {
        return column() + 1 + argsFlatWidth(args) > fmt.preferredLineLength();
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
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printGreedyCommaLines(args, arg);
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

    private void printGreedyCommaLines(NodeList<? extends Expression> args, Void arg) {
        boolean first = true;
        int budget = fmt.preferredLineLength();
        int used = column();
        for (Expression e : args) {
            int need = est(e) + (first ? 0 : 2);
            if (!first && used + need > budget) {
                printer.println();
                printCont();
                used = column();
            } else if (!first) {
                printer.print(", ");
                used += 2;
            }
            e.accept(this, arg);
            used = column();
            first = false;
        }
    }

    private boolean paramsNeedWrap(NodeList<Parameter> ps) {
        return column() + 1 + paramsFlatWidth(ps) > fmt.preferredLineLength();
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
            boolean first = true;
            int budget = fmt.preferredLineLength();
            int used = column();
            for (Parameter p : ps) {
                int need = p.toString().length() + (first ? 0 : 2);
                if (!first && used + need > budget) {
                    printer.println();
                    printCont();
                    used = column();
                } else if (!first) {
                    printer.print(", ");
                    used += 2;
                }
                p.accept(this, arg);
                used = column();
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
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters())) {
            printer.println();
            printCont();
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
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        printTypeParameters(n.getTypeParameters(), arg);
        if (!isNullOrEmpty(n.getTypeParameters())) {
            printer.print(" ");
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
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters())) {
            printer.println();
            printCont();
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
            printer.print(" ");
            n.getBody().get().accept(this, arg);
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
        printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && !isNullOrEmpty(n.getParameters()) && paramsNeedWrap(n.getParameters())) {
            printer.println();
            printCont();
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
                printImplementsClause(n.getImplementedTypes(), arg);
            }
            if (!n.getPermittedTypes().isEmpty()) {
                printPermitsClause(n.getPermittedTypes(), arg);
            }
            printer.println(" {");
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

    private void printImplementsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        int header = column();
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            if (header + 12 + implementsTypesWidth(types) <= fmt.preferredLineLength()) {
                printer.print(" implements ");
                printTypeListInline(types, arg);
                return;
            }
            printer.println();
            printCont();
            printer.print("implements ");
            printTypeListInline(types, arg);
            return;
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
            return;
        }
        printer.println();
        printCont();
        printer.print("implements ");
        types.get(0).accept(this, arg);
        for (int i = 1; i < types.size(); i++) {
            printer.print(",");
            printer.println();
            printNarrowListIndent();
            types.get(i).accept(this, arg);
        }
    }

    private void printPermitsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.isEmpty()) {
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printer.print(" permits ");
            printTypeListInline(types, arg);
            return;
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
    public void visit(com.github.javaparser.ast.expr.ArrayInitializerExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            boolean multi = column() + 1 + argsFlatWidth(n.getValues()) > fmt.preferredLineLength();
            if (multi) {
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
                printCont();
            } else {
                printer.print(" ");
                for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
                    Expression expr = i.next();
                    expr.accept(this, arg);
                    if (i.hasNext()) {
                        printer.print(", ");
                    }
                }
                printer.print(" ");
            }
        }
        printOrphanCommentsEnding(n);
        printer.print("}");
    }

}
