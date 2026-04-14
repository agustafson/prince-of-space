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
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.utils.StringEscapeUtils;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;

import java.util.ArrayList;
import java.util.Comparator;
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
                    boolean hasInterveningComment = hasCommentBetweenLines(n, prevEnd, curStart);
                    boolean currentStatementPrintsCommentBeforeCode =
                            hasLineOrBlockCommentPrintedBeforeNode(s);
                    if (curStart > prevEnd + 1
                            && !hasInterveningComment
                            && !currentStatementPrintsCommentBeforeCode) {
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

    private static boolean hasCommentBetweenLines(BlockStmt block, int startLineExclusive, int endLineExclusive) {
        for (Comment comment : block.getAllContainedComments()) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            int line = comment.getRange().get().begin.line;
            if (line > startLineExclusive && line < endLineExclusive) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLineOrBlockCommentPrintedBeforeNode(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        return comment.getRange().get().begin.line <= node.getRange().get().begin.line;
    }

    private static boolean hasAnyLineOrBlockCommentOnLambda(Node node) {
        if (!(node instanceof LambdaExpr)) {
            return false;
        }
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    private static boolean hasLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    private static Optional<Comment> hoistableArgumentComment(MethodCallExpr mc) {
        if (mc.getArguments().isEmpty()) {
            return Optional.empty();
        }
        if (mc.getArguments().size() > 1) {
            for (Expression argument : mc.getArguments()) {
                Optional<Comment> comment = argument.getComment();
                if (comment.isPresent() && isEmptyLineOrBlockComment(comment.get())) {
                    return comment;
                }
            }
            return Optional.empty();
        }
        Expression only = mc.getArguments().get(0);
        if (only instanceof LambdaExpr) {
            return Optional.empty();
        }
        if (hasLineOrBlockComment(only)) {
            return only.getComment();
        }
        return firstLineOrBlockCommentPrintedBeforeExpression(only);
    }

    private static boolean isEmptyLineOrBlockComment(Comment comment) {
        return (comment instanceof LineComment || comment instanceof BlockComment)
                && comment.getContent().trim().isEmpty();
    }

    private static Optional<Comment> firstLineOrBlockCommentPrintedBeforeExpression(Expression expression) {
        if (expression.getRange().isEmpty()) {
            return Optional.empty();
        }
        int expressionBeginLine = expression.getRange().get().begin.line;
        int expressionBeginColumn = expression.getRange().get().begin.column;
        List<Comment> comments = new ArrayList<>();
        expression.getComment().ifPresent(comments::add);
        comments.addAll(expression.getAllContainedComments());
        return comments.stream()
                .filter(
                        comment ->
                                (comment instanceof LineComment || comment instanceof BlockComment)
                                        && comment.getRange().isPresent()
                                        && (comment.getRange().get().begin.line < expressionBeginLine
                                                || (comment.getRange().get().begin.line
                                                                == expressionBeginLine
                                                        && comment.getRange().get().end.column
                                                                < expressionBeginColumn)))
                .min(
                        Comparator.comparingInt((Comment comment) -> comment.getRange().orElseThrow().begin.line)
                                .thenComparingInt(
                                        comment -> comment.getRange().orElseThrow().begin.column));
    }

    private void printArgumentsWithoutComments(NodeList<? extends Expression> arguments, Void arg) {
        NodeList<Expression> copies = new NodeList<>();
        for (Expression expression : arguments) {
            Expression copy = expression.clone();
            copy.removeComment();
            for (Comment comment : new ArrayList<>(copy.getAllContainedComments())) {
                comment.remove();
            }
            copies.add(copy);
        }
        printArguments(copies, arg);
    }

    private static Optional<Comment> hoistableWrappedChainBaseComment(Expression base) {
        Optional<Comment> comment = base.getComment();
        if (comment.isPresent() && isEmptyLineOrBlockComment(comment.get())) {
            return comment;
        }
        return Optional.empty();
    }

    private void printExpressionWithoutOwnComment(Expression expression, Void arg) {
        Expression copy = expression.clone();
        copy.removeComment();
        copy.accept(this, arg);
    }

    private void printOwnedOrphanComments(Node node, Void arg) {
        for (Comment comment : node.getOrphanComments()) {
            printComment(Optional.of(comment), arg);
        }
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
                // For chains rooted at an unscoped call like make().step(), treat make() as base.
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

    private static int typeArgumentsFlatWidth(NodeList<Type> args) {
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

    private boolean typeArgumentsNeedWrap(NodeList<Type> args) {
        if (isNullOrEmpty(args)) {
            return false;
        }
        int width = column() + 1 + typeArgumentsFlatWidth(args) + 1;
        return width > fmt.preferredLineLength() || width > fmt.maxLineLength();
    }

    @Override
    public void visit(ClassOrInterfaceType n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        if (n.getScope().isPresent()) {
            n.getScope().get().accept(this, arg);
            printer.print(".");
        }
        printAnnotations(n.getAnnotations(), false, arg);
        n.getName().accept(this, arg);
        if (n.isUsingDiamondOperator()) {
            printer.print("<>");
            return;
        }
        NodeList<Type> args = n.getTypeArguments().orElse(null);
        if (isNullOrEmpty(args)) {
            return;
        }
        if (!typeArgumentsNeedWrap(args)) {
            printer.print("<");
            for (Iterator<Type> i = args.iterator(); i.hasNext(); ) {
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
            boolean first = true;
            for (Type t : args) {
                int need = t.toString().length() + (first ? 0 : 2);
                if (first && (column() + need > fmt.preferredLineLength() || wouldExceedMaxLine(need))) {
                    printer.println();
                    printCont();
                } else if (!first && (column() + need > fmt.preferredLineLength() || wouldExceedMaxLine(need))) {
                    printer.print(",");
                    printer.println();
                    printCont();
                } else if (!first) {
                    printer.print(", ");
                }
                t.accept(this, arg);
                first = false;
            }
        } else {
            for (Iterator<Type> i = args.iterator(); i.hasNext(); ) {
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
        int firstModifierLine = n.getModifiers().get(0).getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE);
        NodeList<AnnotationExpr> result = new NodeList<>();
        for (AnnotationExpr annotation : n.getAnnotations()) {
            int annotationColumn = annotation.getRange().map(r -> r.begin.column).orElse(Integer.MIN_VALUE);
            int annotationLine = annotation.getRange().map(r -> r.begin.line).orElse(Integer.MIN_VALUE);
            // Lexical column alone is not enough: after a first format pass, @Override may sit on the
            // line above "public" at the *same* column as "public", so we also treat any annotation
            // starting on a line strictly before the first modifier line as a declaration annotation.
            boolean beforeOnEarlierLine = annotationLine < firstModifierLine;
            boolean beforeOnSameLine = annotationLine == firstModifierLine && annotationColumn < firstModifierColumn;
            if (beforeOnEarlierLine || beforeOnSameLine) {
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
        if (n.getScope().isEmpty()) {
            printOrphanCommentsBeforeThisChildNode(n);
            if (n.getComment().isPresent() && n.getArguments().isEmpty()) {
                printComment(n.getComment(), arg);
            }
            printTypeArgs(n, arg);
            printer.print(n.getNameAsString());
            printArguments(n.getArguments(), arg);
            return;
        }
        MethodCallExpr outer = outermostCall(n);
        if (!outer.equals(n)) {
            return;
        }
        List<MethodCallExpr> calls = chainInOrder(outer);
        Optional<Expression> baseOpt = chainBase(outer);
        if (baseOpt.isEmpty()) {
            super.visit(n, arg);
            return;
        }
        Expression base = baseOpt.get();
        if (base instanceof TextBlockLiteralExpr) {
            printChainInlineWithInlineArgs(base, calls, arg);
            return;
        }
        boolean wrap = mustHardWrapChain(base, calls)
                || mustWrapChain(base, calls)
                || shouldWrapLambdaHeavyChain(base, calls);
        if (!wrap) {
            printOrphanCommentsBeforeThisChildNode(n);
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
            printer.print(mc.getNameAsString());
            printArguments(mc.getArguments(), arg);
        }
    }

    private void printChainInlineWithInlineArgs(Expression base, List<MethodCallExpr> calls, Void arg) {
        base.accept(this, arg);
        for (MethodCallExpr mc : calls) {
            printer.print(".");
            printTypeArgs(mc, arg);
            printer.print(mc.getNameAsString());
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
        int lineStartColumn = column();
        Optional<Comment> hoistedBaseComment = hoistableWrappedChainBaseComment(base);
        if (hoistedBaseComment.isPresent()) {
            printExpressionWithoutOwnComment(base, arg);
        } else {
            base.accept(this, arg);
        }

        if (calls.size() == 1 && isSimpleBase(base)) {
            MethodCallExpr only = calls.get(0);
            printer.print(".");
            printTypeArgs(only, arg);
            only.getName().accept(this, arg);
            if (hasBlockLambdaArgument(only.getArguments())) {
                indentWithAlignToSafe(lineStartColumn + fmt.continuationIndentSize());
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
        indentWithAlignToSafe(contCol);
        for (int i = 0; i < calls.size(); i++) {
            MethodCallExpr mc = calls.get(i);
            if (i > 0) {
                printer.println();
            }
            printOwnedOrphanComments(mc, arg);
            if (i == calls.size() - 1 && hoistedBaseComment.isPresent()) {
                printComment(hoistedBaseComment, arg);
            }
            printOrphanCommentsBeforeThisChildNode(mc);
            Optional<Comment> hoistedComment = hoistableArgumentComment(mc);
            if (hasLineOrBlockComment(mc)) {
                printComment(mc.getComment(), arg);
            } else if (hasLineOrBlockComment(mc.getName())) {
                printComment(mc.getName().getComment(), arg);
            } else if (hoistedComment.isPresent()) {
                printComment(hoistedComment, arg);
            }
            printer.print(".");
            printTypeArgs(mc, arg);
            printer.print(mc.getNameAsString());
            if (hasBlockLambdaArgument(mc.getArguments())) {
                indentWithAlignToSafe(Math.max(contCol, lineStartColumn + fmt.continuationIndentSize()));
                try {
                    printArguments(mc.getArguments(), arg);
                } finally {
                    printer.unindent();
                }
            } else if (hoistedComment.isPresent()) {
                printArgumentsWithoutComments(mc.getArguments(), arg);
            } else {
                printArguments(mc.getArguments(), arg);
            }
        }
        printer.unindent();
    }

    private void indentWithAlignToSafe(int targetColumn) {
        try {
            printer.indentWithAlignTo(targetColumn);
        } catch (IllegalStateException ex) {
            // Defensive fallback for rare AST/layout combinations where alignment underflows.
            printer.indent();
        }
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

    private static boolean anyOperandHasLeadingLineOrBlockComment(List<Expression> parts) {
        for (Expression p : parts) {
            if (hasLeadingLineOrBlockComment(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Greedy {@code ||} / {@code &&} must not emit {@code "|| //"} on one line: line comments end with a
     * newline, so print leading comments first, then the operator, then a comment-free clone of the operand.
     */
    private void printGreedyBinaryOperandWithInterposedLeadingComments(
            Expression operand, String op, Void arg) {
        printer.println();
        printCont();
        printOrphanCommentsBeforeThisChildNode(operand);
        printComment(operand.getComment(), arg);
        printer.print(op);
        printer.print(" ");
        Expression stripped = operand.clone();
        removeAllCommentsFromTree(stripped);
        stripped.accept(this, arg);
    }

    private static void removeAllCommentsFromTree(Node node) {
        new ArrayList<>(node.getOrphanComments()).forEach(Comment::remove);
        node.getComment().ifPresent(Comment::remove);
        for (Node child : new ArrayList<>(node.getChildNodes())) {
            if (!(child instanceof Comment)) {
                removeAllCommentsFromTree(child);
            }
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
            if (!anyOperandHasLeadingLineOrBlockComment(parts)
                    && flat <= fmt.preferredLineLength()
                    && flat <= fmt.maxLineLength()) {
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
            if (!anyOperandHasLeadingLineOrBlockComment(parts)
                    && flat <= fmt.preferredLineLength()
                    && flat <= fmt.maxLineLength()) {
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
            boolean leadingComment = hasLeadingLineOrBlockComment(parts.get(i));
            boolean overPreferred = used + opLen + partLen > budget;
            boolean overMax = used + opLen + partLen > fmt.maxLineLength();
            if (leadingComment) {
                printGreedyBinaryOperandWithInterposedLeadingComments(parts.get(i), op, arg);
                used = column();
                continue;
            }
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
        if (args.size() == 1
                && (hasLeadingLineOrBlockComment(args.get(0))
                        || hasAnyLineOrBlockCommentOnLambda(args.get(0)))) {
            printer.println();
            printCont();
            args.get(0).accept(this, arg);
            return;
        }
        if (args.size() > 1 && hasLineOrBlockComment(args.get(0))) {
            printer.println();
            printCont();
            if (fmt.wrapStyle() == WrapStyle.WIDE) {
                int extraLastLine = fmt.closingParenOnNewLine() ? 2 : 0;
                printGreedyCommaLines(args, arg, 0, false, extraLastLine);
            } else {
                for (Iterator<? extends Expression> i = args.iterator(); i.hasNext(); ) {
                    i.next().accept(this, arg);
                    if (i.hasNext()) {
                        printer.print(",");
                        printer.println();
                        printCont();
                    }
                }
            }
            return;
        }
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
        Expression previous = null;
        while (iter.hasNext()) {
            Expression e = iter.next();
            int need = est(e) + (first ? 0 : 2);
            boolean shouldWrapForLoneLastItem = avoidLoneLastItem && !first && remaining == 2;
            boolean hasInterveningComment = previous != null && hasCommentBetweenNodes(previous, e);
            boolean currentHasLeadingComment = e.getComment().isPresent();
            int lineBudget = budget + (extraLastLineBudget > 0 && remaining == 1 ? extraLastLineBudget : 0);
            if (!first
                    && (column() + need > lineBudget
                            || wouldExceedMaxLine(need)
                            || shouldWrapForLoneLastItem
                            || hasInterveningComment
                            || currentHasLeadingComment)) {
                printer.print(",");
                printer.println();
                printCont();
            } else if (!first) {
                printer.print(", ");
            }
            e.accept(this, arg);
            first = false;
            remaining--;
            previous = e;
        }
    }

    private static boolean hasCommentBetweenNodes(Node previous, Node current) {
        if (previous.getRange().isEmpty() || current.getRange().isEmpty()) {
            return false;
        }
        int prevEnd = previous.getRange().get().end.line;
        int curStart = current.getRange().get().begin.line;
        if (curStart <= prevEnd + 1) {
            return false;
        }
        Optional<Node> parent = current.getParentNode();
        if (parent.isEmpty()) {
            return false;
        }
        for (Comment comment : parent.get().getAllContainedComments()) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            int line = comment.getRange().get().begin.line;
            if (line >= prevEnd && line < curStart) {
                return true;
            }
        }
        return false;
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
        printThrowsClause(n.getThrownExceptions(), arg);
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
        printThrowsClause(n.getThrownExceptions(), arg);
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
                printer.print(" extends");
                printInlineTypeClauseList(n.getExtendedTypes(), arg);
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
            printer.print(" implements");
            printInlineTypeClauseList(types, arg);
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
            printer.print(" permits");
            printInlineTypeClauseList(types, arg);
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

    private void printInlineTypeClauseList(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.size() == 1 && hasLeadingLineOrBlockComment(types.get(0))) {
            printer.println();
            printCont();
            types.get(0).accept(this, arg);
            return;
        }
        printer.print(" ");
        printTypeListInline(types, arg);
    }

    private static int referenceTypesFlatWidth(NodeList<ReferenceType> types) {
        int w = 0;
        boolean first = true;
        for (ReferenceType t : types) {
            if (!first) {
                w += 2;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    private void printThrowsClause(NodeList<ReferenceType> types, Void arg) {
        if (isNullOrEmpty(types)) {
            return;
        }
        int inline = column() + 7 + referenceTypesFlatWidth(types);
        if (inline <= fmt.preferredLineLength() && inline <= fmt.maxLineLength()) {
            printer.print(" throws ");
            for (Iterator<ReferenceType> i = types.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.NARROW) {
            printer.println();
            printCont();
            printer.print("throws");
            for (int i = 0; i < types.size(); i++) {
                printer.println();
                printNarrowListIndent();
                types.get(i).accept(this, arg);
                if (i < types.size() - 1) {
                    printer.print(",");
                }
            }
            return;
        }
        printer.println();
        printCont();
        printer.print("throws ");
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printReferenceTypeListGreedy(types, arg);
        } else {
            types.get(0).accept(this, arg);
            for (int i = 1; i < types.size(); i++) {
                printer.print(",");
                printer.println();
                printCont();
                types.get(i).accept(this, arg);
            }
        }
    }

    private void printReferenceTypeListGreedy(NodeList<ReferenceType> types, Void arg) {
        int budget = fmt.preferredLineLength() - 2;
        boolean first = true;
        for (ReferenceType t : types) {
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

    private static int referenceTypesUnionFlatWidth(NodeList<ReferenceType> types) {
        int w = 0;
        boolean first = true;
        for (ReferenceType t : types) {
            if (!first) {
                w += 3;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    private void printUnionTypeListGreedy(NodeList<ReferenceType> types, Void arg) {
        int budget = fmt.preferredLineLength() - 2;
        boolean first = true;
        for (ReferenceType t : types) {
            int need = t.toString().length() + (first ? 0 : 3);
            if (!first && (column() + need > budget || wouldExceedMaxLine(need))) {
                printer.println();
                printCont();
                printer.print("| ");
            } else if (!first) {
                printer.print(" | ");
            }
            t.accept(this, arg);
            first = false;
        }
    }

    @Override
    public void visit(UnionType n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), true, arg);
        NodeList<ReferenceType> types = n.getElements();
        if (isNullOrEmpty(types)) {
            return;
        }
        int inline = column() + referenceTypesUnionFlatWidth(types);
        if (inline <= fmt.preferredLineLength() && inline <= fmt.maxLineLength()) {
            boolean first = true;
            for (ReferenceType t : types) {
                if (!first) {
                    printer.print(" | ");
                }
                first = false;
                t.accept(this, arg);
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.NARROW) {
            printer.println();
            printCont();
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) {
                    printer.println();
                    printNarrowListIndent();
                    printer.print("| ");
                }
                types.get(i).accept(this, arg);
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printUnionTypeListGreedy(types, arg);
            return;
        }
        types.get(0).accept(this, arg);
        for (int i = 1; i < types.size(); i++) {
            printer.println();
            printCont();
            printer.print("| ");
            types.get(i).accept(this, arg);
        }
    }

    @Override
    public void visit(AssertStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("assert ");
        n.getCheck().accept(this, arg);
        if (n.getMessage().isPresent()) {
            Expression msg = n.getMessage().get();
            printer.print(" : ");
            printAssertMessageRespectingMaxLine(msg, arg);
        }
        printer.print(";");
    }

    private void printAssertMessageRespectingMaxLine(Expression msg, Void arg) {
        if (column() + est(msg) > fmt.maxLineLength()) {
            printer.println();
            printCont();
        }
        if (msg instanceof StringLiteralExpr sl) {
            if (column() + sl.toString().length() > fmt.maxLineLength()) {
                printWrappedStringLiteralChunks(sl.getValue());
                return;
            }
        }
        msg.accept(this, arg);
    }

    /**
     * Emits a chain of string literals joined by {@code +} so each physical line stays within
     * {@link FormatterConfig#maxLineLength()} (assert message must remain a single expression).
     */
    private void printWrappedStringLiteralChunks(String raw) {
        int i = 0;
        while (i < raw.length()) {
            if (i > 0) {
                printer.println();
                printCont();
                printer.print("+ ");
            }
            int maxRoom = fmt.maxLineLength() - column() - 2;
            if (maxRoom < 1) {
                printer.println();
                printCont();
                maxRoom = fmt.maxLineLength() - column() - 2;
            }
            int grow = i;
            while (grow < raw.length()) {
                int cp = raw.codePointAt(grow);
                int growNext = grow + Character.charCount(cp);
                String trial = raw.substring(i, growNext);
                int trialLen = StringEscapeUtils.escapeJava(trial).length() + 2;
                if (trialLen > maxRoom) {
                    break;
                }
                grow = growNext;
            }
            if (grow == i) {
                grow = i + Character.charCount(raw.codePointAt(i));
            }
            String piece = raw.substring(i, grow);
            printer.print("\"");
            printer.print(StringEscapeUtils.escapeJava(piece));
            printer.print("\"");
            i = grow;
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
    public void visit(VariableDeclarator n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        n.findAncestor(NodeWithVariables.class)
                .ifPresent(
                        ancestor ->
                                ((NodeWithVariables<?>) ancestor)
                                        .getMaximumCommonType()
                                        .ifPresent(
                                                commonType -> {
                                                    final Type type = n.getType();
                                                    ArrayType arrayType = null;
                                                    for (int i = commonType.getArrayLevel();
                                                            i < type.getArrayLevel();
                                                            i++) {
                                                        if (arrayType == null) {
                                                            arrayType = (ArrayType) type;
                                                        } else {
                                                            arrayType =
                                                                    (ArrayType) arrayType.getComponentType();
                                                        }
                                                        printAnnotations(arrayType.getAnnotations(), true, arg);
                                                        printer.print("[]");
                                                    }
                                                }));
        if (n.getInitializer().isPresent()) {
            Expression init = n.getInitializer().get();
            printer.print(" =");
            if (hasLeadingLineOrBlockComment(init)) {
                printer.println();
                printCont();
            } else {
                printer.print(" ");
            }
            init.accept(this, arg);
        }
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printLambdaParameters(n, arg);
        printer.print(" -> ");
        if (n.getBody() instanceof BlockStmt block) {
            if (block.getStatements() == null || block.getStatements().isEmpty()) {
                printer.print("{}");
                printOrphanCommentsEnding(block);
                printOrphanCommentsEnding(n);
                return;
            }
            printer.print("{");
            printer.println();
            printer.indent();
            Statement prev = null;
            for (Statement s : block.getStatements()) {
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
            printOrphanCommentsEnding(block);
            printer.unindent();
            printer.print("}");
        } else if (n.getBody() instanceof ExpressionStmt es) {
            // Expression lambdas are stored as ExpressionStmt; must not print a statement terminator.
            es.getExpression().accept(this, arg);
        } else {
            n.getBody().accept(this, arg);
        }
        printOrphanCommentsEnding(n);
    }

    private void printLambdaParameters(LambdaExpr n, Void arg) {
        if (n.isEnclosingParameters()) {
            printer.print("(");
        }
        NodeList<Parameter> ps = n.getParameters();
        if (!isNullOrEmpty(ps) && paramsNeedWrap(ps)) {
            printParametersList(ps, arg);
            if (fmt.closingParenOnNewLine()) {
                printer.println();
            }
        } else {
            for (int i = 0; i < ps.size(); i++) {
                ps.get(i).accept(this, arg);
                if (i < ps.size() - 1) {
                    printer.print(", ");
                }
            }
        }
        if (n.isEnclosingParameters()) {
            printer.print(")");
        }
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
        printOrphanCommentsBeforeThisChildNode(entry);
        printComment(entry.getComment(), arg);
        if (entry.getLabels().isEmpty()) {
            printer.print("default");
        } else {
            printer.print("case ");
            NodeList<Expression> labels = entry.getLabels();
            // Wrap labels using the same comma-list logic as arguments (respects preferred/max and wrapStyle).
            printCommaSeparatedExprs(labels, arg);
        }
        entry.getGuard().ifPresent(
                guard -> {
                    int flat = column() + 6 + est(guard); // " when " + guard
                    if (flat <= fmt.preferredLineLength() && flat <= fmt.maxLineLength()) {
                        printer.print(" when ");
                        guard.accept(this, arg);
                        return;
                    }
                    printer.println();
                    printCont();
                    printer.print("when ");
                    guard.accept(this, arg);
                });
        if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
            printer.print(":");
            return;
        }
        printer.print(" ->");
        NodeList<Statement> stmts = entry.getStatements();
        if (stmts.isEmpty()) {
            return;
        }
        boolean multilineBody =
                stmts.size() > 1 || hasLeadingLineOrBlockComment(stmts.get(0));
        if (multilineBody) {
            printer.println();
            printer.indent();
            for (Statement s : stmts) {
                s.accept(this, arg);
                printer.println();
            }
            printer.unindent();
        } else {
            printer.print(" ");
            stmts.get(0).accept(this, arg);
        }
    }

    /**
     * Line/block comments on nodes printed immediately after {@code =} or {@code ->} without a line
     * break can be re-attached to a different AST node on the next parse. Use a continuation line
     * before printing such nodes.
     */
    private static boolean hasLeadingLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        int commentLine = comment.getRange().get().begin.line;
        int nodeLine = node.getRange().get().begin.line;
        if (commentLine < nodeLine) {
            return true;
        }
        if (commentLine > nodeLine) {
            return false;
        }
        int commentEndColumn = comment.getRange().get().end.column;
        int nodeBeginColumn = node.getRange().get().begin.column;
        return commentEndColumn < nodeBeginColumn;
    }

}
