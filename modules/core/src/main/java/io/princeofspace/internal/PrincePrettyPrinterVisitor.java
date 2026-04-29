package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.utils.PositionUtils;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Width-aware pretty printer driven by {@link FormatterConfig}. Extends JavaParser's default visitor
 * and overrides layout for chains, wrapping, type clauses, try-with-resources, and array literals.
 *
 * <p>Acts as a thin coordinator: each {@code visit} method delegates to a focused formatter class
 * through a shared {@link LayoutContext}.
 *
 * <p>Canonical rules cross-reference (see {@code docs/canonical-formatting-rules.md}):
 * <ul>
 *   <li>R1: idempotency is mandatory</li>
 *   <li>R2: braces and brace placement (K&amp;R, forced bodies)</li>
 *   <li>R3: block indent and {@code 2 * indentSize} continuation indent (see {@link
 *       io.princeofspace.model.FormatterConfig#continuationIndentSize()})</li>
 *   <li>R4: line length as wrap trigger; overflow when no safe break</li>
 *   <li>R5: construct-uniform {@code wrapStyle} semantics</li>
 *   <li>R6: binary/operator chains (operators at continuation line start)</li>
 *   <li>R7: method chains (one {@code .segment()} per line; chain indent is one {@code indentSize})</li>
 *   <li>R8: closing delimiter placement for wrapped lists</li>
 *   <li>R9: blank-line normalization</li>
 *   <li>R10: comment and type-use annotation safety</li>
 * </ul>
 */
@SuppressWarnings("VoidUsed")
final class PrincePrettyPrinterVisitor extends DefaultPrettyPrinterVisitor {
    private static final int TERNARY_OPERATOR_WIDTH = 3; // " ? " / " : "
    private static final int SINGLE_ITEM_COUNT = 1;
    private static final int SWITCH_GUARD_KEYWORD_WIDTH = 6; // " when "
    /** Width of {@code for (} in header flat-width and wrap heuristics. */
    private static final int FOR_LOOP_OPEN_PREFIX_WIDTH = 5;
    /** Width of the closing {@code )} in {@code for (...)} one-line width estimates. */
    private static final int FOR_LOOP_HEADER_CLOSING_PAREN_WIDTH = 1;
    /**
     * Width of {@code " : "} between variable and iterable in a one-line for-each width estimate
     * (unwrapped form uses a space on each side of {@code :}).
     */
    private static final int FOR_EACH_INLINE_SEPARATOR_WIDTH = 3;

    private final FormatterConfig fmt;
    final LayoutContext ctx;
    private final CommentUtils commentUtils;
    private final BinaryExprFormatter binaryExprFormatter;
    private final MethodChainFormatter methodChainFormatter;
    private final ArgumentListFormatter argumentListFormatter;
    private final TypeClauseFormatter typeClauseFormatter;
    private final DeclarationFormatter declarationFormatter;
    private final StringLiteralFormatter stringLiteralFormatter;
    private final ArrayInitializerFormatter arrayInitializerFormatter;

    /**
     * When {@code > 0}, {@link ArgumentListFormatter} continuation lines rely on printer indent from
     * {@link #enterWrappedDelimitedListScope()} instead of explicit {@link LayoutContext#printCont()}
     * so nested wrapped {@code (...)} lists stack R3 continuation correctly.
     */
    private int wrappedDelimitedListScopeDepth;
    /** Tracks argument-list opener line numbers so nested co-line closers can compact to one closer run. */
    private final Deque<Integer> argumentListOpenerLines = new ArrayDeque<>();
    /** True while printing a co-line nested closer run like {@code ));} on a single closer line. */
    private boolean compactArgumentCloserRunActive;
    /** Effective start column for a continuation line produced by printCont/printRawContinuation. */
    private int continuationLineStartColumn = -1;

    /**
     * Line number on which {@link #continuationLineStartColumn} was recorded. The recorded column is
     * only meaningful for the immediately following nested wrapped {@code (...)} list on the same
     * line; once the printer moves to a different line (a new statement, a regenerated continuation,
     * or any inline newline) the stored column becomes stale and must not be consumed by
     * {@link #enterWrappedDelimitedListScope()}, otherwise nested call indents stack two continuation
     * units in one transition (see scenario 50 / Rule 3 max-jump property).
     */
    private int continuationLineStartLine = -1;

    PrincePrettyPrinterVisitor(PrinterConfiguration configuration, FormatterConfig fmt) {
        super(configuration);
        this.fmt = fmt;
        this.ctx = new LayoutContext(fmt, printer, this);
        this.commentUtils = new CommentUtils();
        this.binaryExprFormatter = new BinaryExprFormatter(ctx, commentUtils);
        this.methodChainFormatter = new MethodChainFormatter(ctx, commentUtils);
        this.argumentListFormatter = new ArgumentListFormatter(ctx, fmt, commentUtils, methodChainFormatter);
        this.typeClauseFormatter = new TypeClauseFormatter(ctx, fmt, commentUtils);
        this.declarationFormatter =
                new DeclarationFormatter(ctx, fmt, commentUtils, argumentListFormatter, typeClauseFormatter);
        this.stringLiteralFormatter = new StringLiteralFormatter(ctx);
        this.arrayInitializerFormatter =
                new ArrayInitializerFormatter(ctx, argumentListFormatter, methodChainFormatter);
    }

    // ── bridge methods for LayoutContext ───────────────────────────────────────
    // DefaultPrettyPrinterVisitor keeps useful helpers (modifiers, type args, arguments) as protected;
    // delegates in other packages cannot call them, so we re-expose a minimal surface here.

    /** Pushes two logical indent levels for a wrapped {@code (...)} list body (R3). */
    void enterWrappedDelimitedListScope() {
        if (continuationLineStartColumn >= 0
                && continuationLineStartLine == printer.getCursor().line) {
            int target = continuationLineStartColumn + fmt.continuationIndentSize();
            try {
                printer.indentWithAlignTo(target);
                printer.indentWithAlignTo(target);
            } catch (IllegalStateException ex) {
                printer.indent();
                printer.indent();
            }
            continuationLineStartColumn = -1;
            continuationLineStartLine = -1;
        } else {
            printer.indent();
            printer.indent();
        }
        wrappedDelimitedListScopeDepth++;
    }

    /** Pops {@link #enterWrappedDelimitedListScope()}. */
    void exitWrappedDelimitedListScope() {
        wrappedDelimitedListScopeDepth--;
        printer.unindent();
        printer.unindent();
    }

    boolean isWrappedDelimitedListScopeActive() {
        return wrappedDelimitedListScopeDepth > 0;
    }

    void markContinuationLineStartColumn(int column) {
        continuationLineStartColumn = column;
        continuationLineStartLine = printer.getCursor().line;
    }

    void doPrintComment(Optional<Comment> comment, Void arg) {
        printComment(comment, arg);
    }

    @SuppressWarnings("unchecked")
    void doPrintModifiers(NodeList<?> modifiers) {
        printModifiers((NodeList<com.github.javaparser.ast.Modifier>) modifiers);
    }

    void doPrintMemberAnnotations(NodeList<AnnotationExpr> annotations, Void arg) {
        printMemberAnnotations(annotations, arg);
    }

    void doPrintAnnotations(NodeList<AnnotationExpr> annotations, boolean lineBreaks, Void arg) {
        printAnnotations(annotations, lineBreaks, arg);
    }

    void doPrintTypeArgs(com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments<?> node, Void arg) {
        printTypeArgs(node, arg);
    }

    <T extends Expression> void doPrintArguments(NodeList<T> args, Void arg) {
        printArguments(args, arg);
    }

    void doPrintMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        printMembers(members, arg);
    }

    void doPrintCompactClassMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        printCompactClassMembers(members, arg);
    }

    void doPrintOrphanCommentsEnding(Node n) {
        printOrphanCommentsEnding(n);
    }

    /**
     * Like the superclass, but removes orphan comments after printing so they are not still attached
     * to the parent when the AST is re-parsed and printed again (otherwise trailing line comments in
     * constructs like {@code @SuppressWarnings({ ... })} multiply on each format pass).
     *
     * <p>R1 + R10: duplicate orphan emission breaks idempotency; removal keeps comment placement stable
     * across {@code format(format(x))}.
     */
    @Override
    protected void printOrphanCommentsEnding(final Node node) {
        if (!getOption(ConfigOption.PRINT_COMMENTS).isPresent()) {
            return;
        }
        List<Node> everything = new ArrayList<>(node.getChildNodes());
        PositionUtils.sortByBeginPosition(everything);
        if (everything.isEmpty()) {
            return;
        }
        int commentsAtEnd = 0;
        boolean findingComments = true;
        while (findingComments && commentsAtEnd < everything.size()) {
            Node last = everything.get(everything.size() - 1 - commentsAtEnd);
            findingComments = (last instanceof Comment);
            if (findingComments) {
                commentsAtEnd++;
            }
        }
        for (int i = 0; i < commentsAtEnd; i++) {
            Node c = everything.get(everything.size() - commentsAtEnd + i);
            c.accept(this, null);
            if (c instanceof Comment comment && comment.isOrphan()) {
                comment.remove();
            }
        }
    }

    /**
     * Dispatch to JavaParser's stock printer for nodes we sometimes need without re-entering custom
     * overrides (e.g. {@link MethodChainFormatter} falling back for odd scopes). Only {@link BinaryExpr}
     * and {@link MethodCallExpr} are routed — other node kinds should use {@code accept(this, arg)}.
     */
    void defaultVisit(Node node, Void arg) {
        if (node instanceof BinaryExpr n) {
            super.visit(n, arg);
        } else if (node instanceof MethodCallExpr n) {
            super.visit(n, arg);
        }
    }

    /**
     * Overrides the default to {@link Comment#remove() remove} each orphan comment from the AST
     * after it is printed. Without removal, the same orphan stays in the parent's children and
     * orphan-comment lists and can be re-discovered by later printing passes (e.g.
     * {@link DeclarationFormatter#drainOrphanCommentsBeforeFirstBodyElement} or
     * {@link #printOrphanCommentsEnding}),
     * causing comment duplication that prevents idempotent formatting.
     *
     * <p>R1 + R10: JavaParser keeps orphan {@link Comment} nodes as children of the parent statement;
     * we print them in source order then strip orphans so a later pass cannot print them twice.
     */
    @Override
    protected void printOrphanCommentsBeforeThisChildNode(final Node node) {
        if (node instanceof Comment) {
            return;
        }
        @Nullable Node parent = node.getParentNode().orElse(null);
        if (parent == null) {
            return;
        }
        List<Node> everything = new ArrayList<>(parent.getChildNodes());
        PositionUtils.sortByBeginPosition(everything);
        int positionOfTheChild = -1;
        for (int i = 0; i < everything.size(); i++) {
            if (Objects.equals(everything.get(i), node)) {
                positionOfTheChild = i;
                break;
            }
        }
        if (positionOfTheChild == -1) {
            return;
        }
        int positionOfPreviousChild = -1;
        for (int i = positionOfTheChild - 1; i >= 0 && positionOfPreviousChild == -1; i--) {
            if (!(everything.get(i) instanceof Comment)) {
                positionOfPreviousChild = i;
            }
        }
        List<Comment> toPrint = new ArrayList<>();
        for (int i = positionOfPreviousChild + 1; i < positionOfTheChild; i++) {
            if (everything.get(i) instanceof Comment c) {
                toPrint.add(c);
            }
        }
        boolean keepContinuationAfterOrphanComment = isAnnotationArrayValueContext(parent);
        for (Comment c : toPrint) {
            if (keepContinuationAfterOrphanComment && c instanceof BlockComment blockComment) {
                printNormalizedAnnotationArrayBlockComment(blockComment);
            } else {
                c.accept(this, null);
            }
            if (keepContinuationAfterOrphanComment) {
                printCont();
            }
            if (c.isOrphan()) {
                c.remove();
            }
        }
    }

    private static boolean isAnnotationArrayValueContext(Node parent) {
        if (!(parent instanceof ArrayInitializerExpr)) {
            return false;
        }
        return parent.getParentNode()
                .map(ancestor -> ancestor instanceof MemberValuePair || ancestor instanceof SingleMemberAnnotationExpr)
                .orElse(false);
    }

    private void printNormalizedAnnotationArrayBlockComment(BlockComment blockComment) {
        String[] lines = blockComment.getContent().split("\\R", -1);
        printer.print("/*");
        printer.println();
        for (int i = 0; i < lines.length; i++) {
            String normalized = lines[i].stripLeading();
            if (normalized.isEmpty() && i == lines.length - 1) {
                continue;
            }
            if (normalized.isEmpty()) {
                printer.print(" *");
            } else if (normalized.startsWith("*")) {
                printer.print(" " + normalized);
            } else {
                printer.print(" * " + normalized);
            }
            printer.println();
        }
        printer.print(" */");
    }

    @Override
    protected void printMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        @Nullable BodyDeclaration<?> prev = null;
        for (BodyDeclaration<?> member : members) {
            if (prev != null) {
                printer.println();
                // R9: keep one visual separator between member declarations, but keep field groups compact.
                if (!(prev instanceof FieldDeclaration && member instanceof FieldDeclaration)) {
                    printer.println();
                }
            }
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
            @Nullable Statement prev = null;
            for (Statement s : n.getStatements()) {
                // R9 + R10: preserve intentional blank lines, but do not manufacture spacing around
                // comments that are already emitted before the next statement.
                if (prev != null && prev.getRange().isPresent() && s.getRange().isPresent()) {
                    int prevEnd = prev.getRange().get().end.line;
                    int curStart = s.getRange().get().begin.line;
                    boolean hasInterveningComment = commentUtils.hasCommentBetweenStatements(n, prev, s);
                    boolean currentStatementPrintsCommentBeforeCode =
                            commentUtils.hasLineOrBlockCommentPrintedBeforeNode(s);
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

    @Override
    public void visit(ForStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        // R4: switch between flat vs wrapped header based on a one-line width estimate.
        boolean headerWrap = forStmtHeaderNeedsWrap(n);
        printer.print("for (");
        if (headerWrap) {
            if (fmt.wrapStyle() == WrapStyle.WIDE) {
                printForStmtHeaderWrappedWide(n, arg);
            } else {
                printForStmtHeaderWrappedBalanced(n, arg);
            }
        } else {
            printForStmtHeaderUnwrapped(n, arg);
        }
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        // R4: wrap long for-each headers; wrapped iterable starts on a continuation line.
        boolean headerWrap = forEachHeaderNeedsWrap(n);
        printer.print("for (");
        n.getVariable().accept(this, arg);
        if (headerWrap) {
            printer.print(" :");
            printer.println();
            printCont();
            n.getIterable().accept(this, arg);
        } else {
            printer.print(" : ");
            n.getIterable().accept(this, arg);
        }
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        // try-with-resources: each resource is a full statement; R3 continuation lines after ';'.
        // R4: wrap when the header exceeds line length (multi-resource is common to wrap).
        // R8: optional ')' on its own line for multi-resource headers (mirrors wrapped arg lists).
        printer.print("try ");
        if (!n.getResources().isEmpty()) {
            printer.print("(");
            Iterator<Expression> resources = n.getResources().iterator();
            while (resources.hasNext()) {
                resources.next().accept(this, arg);
                if (resources.hasNext()) {
                    printer.print(";");
                    printer.println();
                    printCont();
                }
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

    private boolean forStmtHeaderNeedsWrap(ForStmt n) {
        return column() + FOR_LOOP_OPEN_PREFIX_WIDTH + forStmtHeaderInnerFlatWidth(n) + FOR_LOOP_HEADER_CLOSING_PAREN_WIDTH
                > fmt.lineLength();
    }

    private int forStmtHeaderInnerFlatWidth(ForStmt n) {
        int w = 0;
        boolean first = true;
        if (n.getInitialization() != null) {
            for (Expression e : n.getInitialization()) {
                if (!first) {
                    w += 2;
                }
                first = false;
                w += WidthMeasurer.flatWidth(e, fmt);
            }
        }
        w += 2; // "; "
        if (n.getCompare().isPresent()) {
            w += WidthMeasurer.flatWidth(n.getCompare().get(), fmt);
        }
        w += 2; // "; "
        first = true;
        if (n.getUpdate() != null) {
            for (Expression e : n.getUpdate()) {
                if (!first) {
                    w += 2;
                }
                first = false;
                w += WidthMeasurer.flatWidth(e, fmt);
            }
        }
        return w;
    }

    private void printForStmtHeaderUnwrapped(ForStmt n, Void arg) {
        if (n.getInitialization() != null) {
            for (Iterator<Expression> i = n.getInitialization().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print("; ");
        if (n.getCompare().isPresent()) {
            n.getCompare().get().accept(this, arg);
        }
        printer.print("; ");
        if (n.getUpdate() != null) {
            for (Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
    }

    // R3: each wrapped header part starts at continuation column (2 * indentSize from block start).
    private void printForStmtHeaderWrappedBalanced(ForStmt n, Void arg) {
        printer.println();
        printCont();
        if (n.getInitialization() != null) {
            for (Iterator<Expression> i = n.getInitialization().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(";");
        printer.println();
        printCont();
        if (n.getCompare().isPresent()) {
            n.getCompare().get().accept(this, arg);
        }
        printer.print(";");
        printer.println();
        printCont();
        if (n.getUpdate() != null) {
            for (Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
    }

    // R5: WIDE and BALANCED use the same for-loop header shape here (one clause per line when wrapped).
    private void printForStmtHeaderWrappedWide(ForStmt n, Void arg) {
        printForStmtHeaderWrappedBalanced(n, arg);
    }

    private boolean forEachHeaderNeedsWrap(ForEachStmt n) {
        int oneLineWidth = FOR_LOOP_OPEN_PREFIX_WIDTH
                + n.getVariable().toString().length()
                + FOR_EACH_INLINE_SEPARATOR_WIDTH
                + WidthMeasurer.flatWidth(n.getIterable(), fmt)
                + FOR_LOOP_HEADER_CLOSING_PAREN_WIDTH;
        return column() + oneLineWidth > fmt.lineLength();
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
        ctx.printCont();
    }

    @Override
    protected void printTypeParameters(NodeList<TypeParameter> typeParameters, Void arg) {
        argumentListFormatter.printTypeParameters(typeParameters, arg);
    }

    // Type arguments: comma-separated list in angle brackets. R4 width check; R5 WIDE vs BALANCED/NARROW
    // shape; R3 printCont for continuation lines.
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
        if (n.getTypeArguments().isEmpty()) {
            return;
        }
        NodeList<Type> args = n.getTypeArguments().get();
        if (isNullOrEmpty(args)) {
            return;
        }
        if (!argumentListFormatter.typeArgumentsNeedWrap(args)) {
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
                if (first && column() + need > fmt.lineLength()) {
                    printer.println();
                    printCont();
                } else if (!first && column() + need > fmt.lineLength()) {
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

    // R10: if the member value has a leading line/block comment, break before it so the comment stays
    // next to the value (not glued to '@Name(').
    @Override
    public void visit(SingleMemberAnnotationExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("@");
        n.getName().accept(this, arg);
        printer.print("(");
        if (commentUtils.hasLeadingLineOrBlockComment(n.getMemberValue())) {
            printer.println();
            printCont();
            n.getMemberValue().accept(this, arg);
            printer.println();
            printCont();
        } else {
            n.getMemberValue().accept(this, arg);
        }
        printer.print(")");
    }

    // R10: any MemberValuePair with a line/block comment forces one pair per line at continuation depth.
    @Override
    public void visit(NormalAnnotationExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("@");
        n.getName().accept(this, arg);
        NodeList<MemberValuePair> pairs = n.getPairs();
        if (pairs.isEmpty()) {
            return;
        }
        printer.print("(");
        boolean hasCommentedPair = false;
        for (MemberValuePair p : pairs) {
            if (commentUtils.hasLineOrBlockComment(p)) {
                hasCommentedPair = true;
                break;
            }
        }
        if (!hasCommentedPair) {
            for (Iterator<MemberValuePair> i = pairs.iterator(); i.hasNext(); ) {
                i.next().accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(")");
            return;
        }
        for (Iterator<MemberValuePair> i = pairs.iterator(); i.hasNext(); ) {
            printer.println();
            printCont();
            i.next().accept(this, arg);
            if (i.hasNext()) {
                printer.print(",");
            }
        }
        printer.println();
        printCont();
        printer.print(")");
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        // R7: all chain-specific wrapping/indent policy is centralized in MethodChainFormatter.
        methodChainFormatter.format(n, arg);
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        // R6 (+ R5): binary/operator-chain wrapping delegates to the shared operator formatter.
        binaryExprFormatter.format(n, arg);
    }

    // Ternary: R4 flat-width test; if wrapped, R6-style continuation with '? ' and ': ' at line start
    // (same left-edge visibility as binary chains). R3 uses printCont for then/else continuations.
    @Override
    public void visit(ConditionalExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        int flat =
                column()
                        + WidthMeasurer.flatWidth(n.getCondition(), fmt)
                        + TERNARY_OPERATOR_WIDTH
                        + WidthMeasurer.flatWidth(n.getThenExpr(), fmt)
                        + TERNARY_OPERATOR_WIDTH
                        + WidthMeasurer.flatWidth(n.getElseExpr(), fmt);
        if (flat <= fmt.lineLength()) {
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
        int openerLine = printer.getCursor().line;
        boolean nestedOpenersAreColine =
                !argumentListOpenerLines.isEmpty() && argumentListOpenerLines.peek() == openerLine;
        argumentListOpenerLines.push(openerLine);
        printer.print("(");
        boolean wrapped = false;
        boolean trailingLambda = false;
        try {
            if (!isNullOrEmpty(args)) {
                wrapped = argumentListFormatter.argsNeedWrap(args);
                trailingLambda = wrapped && argumentListFormatter.shouldUseTrailingLambdaLayout(args);
                if (trailingLambda) {
                    // TDR-021: trailing-lambda keeps the lambda header on the call line. The lambda
                    // visitor emits its own multi-line body and the closer follows it inline, so we
                    // skip the wrapped-list indent scope and the closing-paren newline. We also
                    // clear any stale continuationLineStartColumn so a wrapped inner call inside the
                    // lambda body anchors its indent to the surrounding block, not to a leftover
                    // continuation column from an earlier statement.
                    continuationLineStartColumn = -1;
                    continuationLineStartLine = -1;
                    argumentListFormatter.printTrailingLambdaLayout(args, arg);
                } else {
                    // Only push extra printer indents when this list itself uses explicit continuation lines.
                    // A single wrapped expression argument (e.g. new X("""...""".formatted(...))) must not
                    // activate the scope: inner chains/binary continuations still use printCont relative to the
                    // outer call indent.
                    boolean applyWrappedListIndent =
                            wrapped
                                    && (args.size() > SINGLE_ITEM_COUNT
                                            || (args.size() == SINGLE_ITEM_COUNT
                                                    && (commentUtils.hasLeadingLineOrBlockComment(args.get(0))
                                                            || commentUtils.hasAnyLineOrBlockCommentOnLambda(
                                                                    args.get(0))
                                                            || argumentListFormatter.shouldBreakBeforeSingleWrappedArg(
                                                                    args.get(0)))));
                    if (applyWrappedListIndent) {
                        enterWrappedDelimitedListScope();
                    }
                    try {
                        argumentListFormatter.printCommaSeparatedExprs(args, arg);
                    } finally {
                        if (applyWrappedListIndent) {
                            exitWrappedDelimitedListScope();
                        }
                    }
                }
            }
            // R8: when configured, closing ')' moves to its own line for wrapped argument lists.
            // TDR-016 exception: trailing-lambda layout always closes "})" to match palantir-java-format.
            if (fmt.closingParenOnNewLine() && wrapped && !trailingLambda) {
                if (nestedOpenersAreColine) {
                    if (!compactArgumentCloserRunActive) {
                        printer.println();
                        compactArgumentCloserRunActive = true;
                    }
                } else if (!compactArgumentCloserRunActive) {
                    printer.println();
                }
            }
            printer.print(")");
        } finally {
            argumentListOpenerLines.pop();
            if (!nestedOpenersAreColine) {
                compactArgumentCloserRunActive = false;
            }
        }
    }

    // Bodies, parameters, and type headers for declarations live in DeclarationFormatter (same rules;
    // keeps this visitor smaller).
    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        declarationFormatter.formatConstructor(n, arg);
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        declarationFormatter.formatMethod(n, arg);
    }

    @Override
    public void visit(com.github.javaparser.ast.body.RecordDeclaration n, Void arg) {
        declarationFormatter.formatRecord(n, arg);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        declarationFormatter.formatClassOrInterface(n, arg);
    }

    // Multi-catch: UnionType; R4/R5 list layout delegated to TypeClauseFormatter.
    @Override
    public void visit(UnionType n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), true, arg);
        typeClauseFormatter.formatUnionType(n, arg);
    }

    // Assert must stay one statement; R4: break the message to continuation lines, chunk long string
    // messages into literal fragments joined by '+' (still one expression for the parser).
    @Override
    public void visit(AssertStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("assert ");
        n.getCheck().accept(this, arg);
        if (n.getMessage().isPresent()) {
            printer.print(" : ");
            stringLiteralFormatter.printAssertMessageRespectingMaxLine(n.getMessage().get(), arg);
        }
        printer.print(";");
    }

    void emitChunkedStringLiteral(String raw) {
        stringLiteralFormatter.emitChunkedStringLiteral(raw);
    }

    @Override
    public void visit(EnclosedExpr n, Void arg) {
        if (stringLiteralFormatter.tryFormatLargeStringConcatEnclosed(n, arg)) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, Void arg) {
        declarationFormatter.formatEnum(n, arg);
    }

    @Override
    public void visit(com.github.javaparser.ast.expr.ArrayInitializerExpr n, Void arg) {
        arrayInitializerFormatter.format(n, arg);
    }

    // JavaParser splits int[] a, b; into per-declarator printing; re-emit extra [] and type-use
    // annotations for additional declarators. R4/R3: '=' + initializer may break; R10: type-use
    // annotations on array dimensions stay next to '[]'. R1: huge literal + chains get forced line
    // break so chunking sees stable column (same as visit(StringLiteralExpr)).
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
                                                    @Nullable ArrayType arrayType = null;
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
            if (commentUtils.hasLeadingLineOrBlockComment(init)) {
                printer.println();
                printCont();
            } else {
                boolean inlineWouldOverflow = ctx.column() + 1 + WidthMeasurer.flatWidth(init, fmt) > fmt.lineLength();
                int continuationRhsBudget =
                        Math.max(1, fmt.lineLength() - (fmt.continuationIndentSize() + fmt.indentSize()));
                boolean rhsFitsSingleContinuationLine = WidthMeasurer.flatWidth(init, fmt) <= continuationRhsBudget;
                // Break before long string-like initializers when the combined line would exceed limits.
                // Restrict to string literals, text blocks, and literal-only "+" chains so array/object
                // initializers are not mis-measured via toString().
                int tailWidth = stringLiteralFormatter.tailWidthAfterEqualsForInitializerBreakHeuristic(init);
                boolean longStringLikeInitializer = stringLiteralFormatter.initializerNeedsForcedBreakBeforeChunking(init);
                if ((inlineWouldOverflow && rhsFitsSingleContinuationLine)
                        || (tailWidth >= 0 && ctx.column() + tailWidth > fmt.lineLength())) {
                    printer.println();
                    printCont();
                } else if (longStringLikeInitializer) {
                    // Width heuristics use JavaParser textual forms that can disagree for huge
                    // literal-only + trees; still move the initializer to a continuation line so string
                    // chunking sees the same leading indent on every format pass.
                    printer.println();
                    printCont();
                } else {
                    printer.print(" ");
                }
            }
            init.accept(this, arg);
        }
    }

    // R2: block lambda uses K&R '{'. R1: empty block with comments must print braces+comments so
    // re-parse does not re-attach comment nodes outside the block. R9: optional blank line between
    // statements in block body mirrors method body rules.
    @Override
    public void visit(LambdaExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printLambdaParameters(n, arg);
        printer.print(" -> ");
        if (n.getBody() instanceof BlockStmt block) {
            if (block.getStatements() == null || block.getStatements().isEmpty()) {
                // R1: Empty statement list but comments inside the block must stay between { and }, not
                // after "{}", or the second parse re-attaches them and idempotency breaks.
                if (block.getComment().isPresent() || !block.getOrphanComments().isEmpty()) {
                    printOrphanCommentsBeforeThisChildNode(block);
                    printComment(block.getComment(), arg);
                    printer.print("{");
                    printer.println();
                    printer.indent();
                    printOrphanCommentsEnding(block);
                    printer.unindent();
                    printer.print("}");
                } else {
                    printer.print("{}");
                    printOrphanCommentsEnding(block);
                }
                printOrphanCommentsEnding(n);
                return;
            }
            printer.print("{");
            printer.println();
            printer.indent();
            @Nullable Statement prev = null;
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

    // R8: when lambda params wrap, the closing ')' always goes on its own line so that the trailing
    // ' ->' arrow reads as a continuation marker rather than disappearing into the tail of a long
    // parameter line. The line aligns to the opener '(' column so the closer matches the standard
    // Rule 8 placement (closing delimiter on its own line at the opener's indentation column). This
    // is independent of closingParenOnNewLine, which controls non-lambda call/declaration parens.
    private void printLambdaParameters(LambdaExpr n, Void arg) {
        int openParenStartColumn = 0;
        if (n.isEnclosingParameters()) {
            openParenStartColumn = column();
            printer.print("(");
        }
        NodeList<Parameter> ps = n.getParameters();
        // Unparenthesized lambda params (single param without parens) cannot wrap — there's no
        // comma list and no enclosing delimiters to anchor a continuation. Single parenthesized
        // params likewise have nothing to break on. Only multi-param parenthesized lists wrap.
        if (!isNullOrEmpty(ps)) {
            boolean canWrapParams = n.isEnclosingParameters() && ps.size() > 1;
            if (canWrapParams && argumentListFormatter.paramsNeedWrap(ps)) {
                argumentListFormatter.printParametersListForLambda(ps, arg, openParenStartColumn);
                printer.println();
                ctx.padToColumn0(openParenStartColumn);
            } else {
                for (int i = 0; i < ps.size(); i++) {
                    ps.get(i).accept(this, arg);
                    if (i < ps.size() - 1) {
                        printer.print(", ");
                    }
                }
            }
        }
        if (n.isEnclosingParameters()) {
            printer.print(")");
        }
    }

    // Text blocks: R4 is soft — content is preserved verbatim; we do not reflow string interior.
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
    public void visit(StringLiteralExpr n, Void arg) {
        stringLiteralFormatter.formatStringLiteral(n, arg);
    }

    // Switch expressions: one path uses printSwitchEntry (arrow-style); R4/R5 in ArgumentListFormatter for labels.
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

    @Override
    public void visit(SwitchStmt n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("switch (");
        n.getSelector().accept(this, arg);
        printer.println(") {");
        if (n.getEntries() != null) {
            printer.indent();
            for (SwitchEntry e : n.getEntries()) {
                e.accept(this, arg);
            }
            printer.unindent();
        }
        printer.print("}");
        printOrphanCommentsEnding(n);
    }

    // Classic switch entry (colon) vs arrow — JavaParser encodes both; R5 for case label lists.
    @Override
    public void visit(SwitchEntry n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        String separator = n.getType() == SwitchEntry.Type.STATEMENT_GROUP ? ":" : " ->";
        if (isNullOrEmpty(n.getLabels())) {
            printer.print("default" + separator);
        } else {
            printer.print("case ");
            argumentListFormatter.printCommaSeparatedExprs(n.getLabels(), arg);
            if (n.getLabels().isNonEmpty() && n.isDefault()) {
                printer.print(", default");
            }
            n.getGuard().ifPresent(guard -> printSwitchWhenGuard(guard, arg));
            printer.print(separator);
        }
        printer.println();
        printer.indent();
        if (n.getStatements() != null) {
            for (Statement s : n.getStatements()) {
                s.accept(this, arg);
                printer.println();
            }
        }
        printer.unindent();
    }

    // R4: ' when ' on same line if it fits; else R3 continuation with 'when' at margin.
    private void printSwitchWhenGuard(Expression guard, Void arg) {
        int flat = column() + SWITCH_GUARD_KEYWORD_WIDTH + WidthMeasurer.flatWidth(guard, fmt);
        if (flat <= fmt.lineLength()) {
            printer.print(" when ");
            guard.accept(this, arg);
        } else {
            printer.println();
            printCont();
            printer.print("when ");
            guard.accept(this, arg);
        }
    }

    private void printSwitchEntry(SwitchEntry entry, Void arg) {
        printOrphanCommentsBeforeThisChildNode(entry);
        printComment(entry.getComment(), arg);
        if (entry.getLabels().isEmpty()) {
            printer.print("default");
        } else {
            printer.print("case ");
            NodeList<Expression> labels = entry.getLabels();
            // R4 + R5: same ArgumentListFormatter path as call arguments and type args.
            argumentListFormatter.printCommaSeparatedExprs(labels, arg);
        }
        entry.getGuard().ifPresent(guard -> printSwitchWhenGuard(guard, arg));
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
                stmts.size() > 1 || commentUtils.hasLeadingLineOrBlockComment(stmts.get(0));
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

}
