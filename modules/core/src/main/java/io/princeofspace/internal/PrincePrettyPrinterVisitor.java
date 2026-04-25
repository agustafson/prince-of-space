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
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
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
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
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
import com.github.javaparser.utils.StringEscapeUtils;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Width-aware pretty printer driven by {@link FormatterConfig}. Extends JavaParser's default visitor
 * and overrides layout for chains, wrapping, type clauses, try-with-resources, and array literals.
 *
 * <p>Acts as a thin coordinator: each {@code visit} method delegates to a focused formatter class
 * through a shared {@link LayoutContext}.
 */
final class PrincePrettyPrinterVisitor extends DefaultPrettyPrinterVisitor {
    private static final int TERNARY_OPERATOR_WIDTH = 3; // " ? " / " : "
    private static final int BALANCED_PAREN_HEADROOM_MIN = 16;
    private static final int BALANCED_PAREN_HEADROOM_MAX = 64;
    private static final int BALANCED_PAREN_HEADROOM_DIVISOR = 3;
    private static final int LARGE_STRING_FORCE_BREAK_THRESHOLD = 500;
    private static final int SWITCH_GUARD_KEYWORD_WIDTH = 6; // " when "

    private final FormatterConfig fmt;
    final LayoutContext ctx;
    private final CommentUtils commentUtils;
    private final BinaryExprFormatter binaryExprFormatter;
    private final MethodChainFormatter methodChainFormatter;
    private final ArgumentListFormatter argumentListFormatter;
    private final TypeClauseFormatter typeClauseFormatter;
    private final DeclarationFormatter declarationFormatter;

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
    }

    // ── bridge methods for LayoutContext ───────────────────────────────────────
    // These expose inherited protected methods to package-private delegate classes.

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

    /** Dispatch to the default (superclass) visitor for a node. */
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
     */
    @Override
    protected void printOrphanCommentsBeforeThisChildNode(final Node node) {
        if (node instanceof Comment) {
            return;
        }
        Node parent = node.getParentNode().orElse(null);
        if (parent == null) {
            return;
        }
        List<Node> everything = new ArrayList<>(parent.getChildNodes());
        PositionUtils.sortByBeginPosition(everything);
        int positionOfTheChild = -1;
        for (int i = 0; i < everything.size(); i++) {
            if (everything.get(i) == node) {
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
        for (Comment c : toPrint) {
            c.accept(this, null);
            if (c.isOrphan()) {
                c.remove();
            }
        }
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

    @Override
    protected void printTypeParameters(NodeList<TypeParameter> typeParameters, Void arg) {
        argumentListFormatter.printTypeParameters(typeParameters, arg);
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
        methodChainFormatter.format(n, arg);
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        binaryExprFormatter.format(n, arg);
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        int flat =
                column()
                        + ctx.est(n.getCondition())
                        + TERNARY_OPERATOR_WIDTH
                        + ctx.est(n.getThenExpr())
                        + TERNARY_OPERATOR_WIDTH
                        + ctx.est(n.getElseExpr());
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
        printer.print("(");
        boolean wrapped = false;
        if (!isNullOrEmpty(args)) {
            wrapped = argumentListFormatter.argsNeedWrap(args);
            argumentListFormatter.printCommaSeparatedExprs(args, arg);
        }
        if (fmt.closingParenOnNewLine() && wrapped && !commentUtils.shouldGlueWrappedClosingParen(args)) {
            printer.println();
        }
        printer.print(")");
    }

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

    @Override
    public void visit(UnionType n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), true, arg);
        typeClauseFormatter.formatUnionType(n, arg);
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
        if (column() + ctx.est(msg) > fmt.lineLength()) {
            printer.println();
            printCont();
        }
        if (msg instanceof StringLiteralExpr sl) {
            int quotedLen = StringEscapeUtils.escapeJava(sl.getValue()).length() + 2;
            if (column() + quotedLen > fmt.lineLength()) {
                emitChunkedStringLiteral(sl.getValue());
                return;
            }
        }
        msg.accept(this, arg);
    }

    /**
     * Emits a chain of string literals joined by {@code +} so each physical line stays within
     * {@link FormatterConfig#lineLength()} (assert message must remain a single expression).
     */
    void emitChunkedStringLiteral(String raw) {
        printWrappedStringLiteralChunks(raw);
    }

    /**
     * Very long left-associative {@code "a" + "b" + ...} chains parse to a binary tree whose depth
     * equals the fragment count; JavaParser then overflows the stack while registering the subtree.
     * Above a threshold, emit a balanced parenthesized concat (same merged text, shallower tree).
     */
    private static final int MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS = 128;

    private void printWrappedStringLiteralChunks(String raw) {
        List<String> pieces = collectRawStringPiecesForChunking(raw);
        if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            String first = pieces.get(0);
            int firstPieceLen = StringEscapeUtils.escapeJava(first).length() + 2;
            int firstLineRoom = Math.max(1, fmt.lineLength() - column() - 2);
            if (firstPieceLen > firstLineRoom) {
                pieces = splitRawIntoStringPieces(raw, Math.min(stableMaxRoomAfterPlusPrefix(), firstLineRoom));
            }
            emitLinearStringPiecesFromList(pieces);
        } else {
            emitBalancedParenStringPieces(pieces);
        }
    }

    /**
     * Maximum width (including both {@code "} delimiters and escapes) for a single chunked string
     * literal fragment. Derived only from {@link FormatterConfig} and a fixed worst-case nesting
     * assumption, so the same raw string always splits the same way whether the parser exposes it as
     * one {@link StringLiteralExpr} or a re-parsed {@code +} / parenthesis tree.
     *
     * <p>Budget matches a continuation line {@code + "} layout: up to {@value
     * #WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING} logical block indents, continuation indent, the
     * two-character {@code + } infix, balanced concat left-spine headroom, plus the historical
     * {@code - 2} adjustment.
     */
    private static final int WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING = 4;

    private int stableMaxRoomAfterPlusPrefix() {
        int openingQuoteColumn =
                WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING * fmt.indentSize()
                        + fmt.continuationIndentSize()
                        + 2;
        int maxRoom = fmt.lineLength() - openingQuoteColumn - 2;
        return Math.max(maxRoom, 1);
    }

    /**
     * Split using only the continuation-style budget for every fragment so boundaries depend on
     * indent/config, not {@code column()} (which differs for {@code "} vs {@code (} after a re-parse).
     *
     * <p>When the fragment count exceeds {@link #MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS}, emit uses a
     * balanced parenthesized tree. The left spine prints a run of {@code '('} on the same physical
     * line as the opening quote of the first fragment; reserve bounded headroom derived from
     * {@link FormatterConfig#lineLength()} so the first fragment stays within budget without
     * over-penalizing narrow or wide configurations.
     */
    private int balancedStringConcatParenHeadroom() {
        // Reserve roughly one-third of line length, but keep a practical floor/ceiling.
        return Math.max(
                BALANCED_PAREN_HEADROOM_MIN,
                Math.min(BALANCED_PAREN_HEADROOM_MAX, fmt.lineLength() / BALANCED_PAREN_HEADROOM_DIVISOR));
    }

    private List<String> collectRawStringPiecesForChunking(String raw) {
        int baseMax = stableMaxRoomAfterPlusPrefix();
        List<String> pieces = splitRawIntoStringPieces(raw, baseMax);
        if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            return pieces;
        }
        int headroom = balancedStringConcatParenHeadroom();
        return splitRawIntoStringPieces(
                raw, Math.max(1, baseMax - headroom));
    }

    private static List<String> splitRawIntoStringPieces(String raw, int maxRoom) {
        List<String> pieces = new ArrayList<>();
        if (raw.isEmpty()) {
            pieces.add("");
            return pieces;
        }
        int i = 0;
        while (i < raw.length()) {
            int grow = growPieceEndIndexForChunking(raw, i, maxRoom);
            pieces.add(raw.substring(i, grow));
            i = grow;
        }
        return pieces;
    }

    private static int growPieceEndIndexForChunking(String raw, int i, int maxRoom) {
        int grow = i;
        int preferredBreak = -1;
        while (grow < raw.length()) {
            int cp = raw.codePointAt(grow);
            int growNext = grow + Character.charCount(cp);
            String trial = raw.substring(i, growNext);
            int trialLen = StringEscapeUtils.escapeJava(trial).length() + 2;
            if (trialLen > maxRoom) {
                break;
            }
            grow = growNext;
            if (isPreferredStringChunkBoundary(cp)) {
                preferredBreak = grow;
            }
        }
        if (grow == i) {
            return i + Character.charCount(raw.codePointAt(i));
        }
        // Prefer semantic boundaries (space/punctuation/end-of-line) so words are not split mid-token.
        return preferredBreak > i ? preferredBreak : grow;
    }

    private static boolean isPreferredStringChunkBoundary(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint == '-'
                || codePoint == '_'
                || codePoint == ','
                || codePoint == '.'
                || codePoint == ';'
                || codePoint == ':'
                || codePoint == '!'
                || codePoint == '?'
                || codePoint == ')'
                || codePoint == ']'
                || codePoint == '}';
    }

    /** Multi-line concat for precomputed fragments (line breaks inserted only when emitting). */
    private void emitLinearStringPiecesFromList(List<String> pieces) {
        for (int idx = 0; idx < pieces.size(); idx++) {
            if (idx > 0) {
                printer.println();
                printCont();
                printer.print("+ ");
            }
            String piece = pieces.get(idx);
            printer.print("\"");
            printer.print(StringEscapeUtils.escapeJava(piece));
            printer.print("\"");
        }
    }

    private void emitBalancedParenStringPieces(List<String> pieces) {
        emitBalancedParenStringPiecesImpl(pieces, 0, pieces.size(), true);
    }

    private void emitBalancedParenStringPiecesImpl(List<String> pieces, int lo, int hi, boolean isGlobalFirstLeaf) {
        if (hi - lo == 1) {
            printOneStringLiteralPiece(pieces.get(lo), isGlobalFirstLeaf);
            return;
        }
        int mid = (lo + hi) >>> 1;
        printer.print("(");
        emitBalancedParenStringPiecesImpl(pieces, lo, mid, isGlobalFirstLeaf);
        printer.print(" + ");
        // Right operand follows an explicit infix "+"; do not emit the continuation "+ " prefix used
        // between top-level fragments (would parse as string + unary-plus and break idempotency).
        emitBalancedParenStringPiecesImpl(pieces, mid, hi, true);
        printer.print(")");
    }

    private void printOneStringLiteralPiece(String piece, boolean isGlobalFirstLeaf) {
        if (!isGlobalFirstLeaf) {
            printer.println();
            printCont();
            printer.print("+ ");
        }
        printer.print("\"");
        printer.print(StringEscapeUtils.escapeJava(piece));
        printer.print("\"");
    }

    @Override
    public void visit(EnclosedExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        if (isTopLevelStringConcatChain(n.getInner())
                && mergedStringLiteralChainCharCount(n.getInner()) >= LARGE_STRING_FORCE_BREAK_THRESHOLD) {
            Expression inner = n.getInner();
            while (inner instanceof EnclosedExpr enc) {
                inner = enc.getInner();
            }
            if (mustKeepParensAroundConcatForParent(n)) {
                printer.print("(");
                inner.accept(this, arg);
                printer.print(")");
            } else {
                inner.accept(this, arg);
            }
            printOrphanCommentsEnding(n);
            return;
        }
        super.visit(n, arg);
    }

    private static boolean mustKeepParensAroundConcatForParent(EnclosedExpr n) {
        return n.getParentNode()
                .map(
                        p ->
                                (p instanceof MethodCallExpr mc && mc.getScope().map(s -> s == n).orElse(false))
                                        || (p instanceof ArrayAccessExpr aa && aa.getName() == n))
                .orElse(false);
    }

    @Override
    public void visit(EnumDeclaration n, Void arg) {
        declarationFormatter.formatEnum(n, arg);
    }

    @Override
    public void visit(com.github.javaparser.ast.expr.ArrayInitializerExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printer.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            int arrayFlat = column() + methodChainFormatter.argsFlatWidth(n.getValues()) + 2;
            boolean multi =
                    arrayFlat > fmt.lineLength();
            if (multi) {
                if (fmt.wrapStyle() == WrapStyle.WIDE) {
                    // Greedy inline packing
                    argumentListFormatter.printGreedyCommaLines(n.getValues(), arg, 2, true, 0);
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
            if (commentUtils.hasLeadingLineOrBlockComment(init)) {
                printer.println();
                printCont();
            } else {
                boolean inlineWouldOverflow = ctx.column() + 1 + ctx.est(init) > fmt.lineLength();
                int continuationRhsBudget =
                        Math.max(1, fmt.lineLength() - (fmt.continuationIndentSize() + fmt.indentSize()));
                boolean rhsFitsSingleContinuationLine = ctx.est(init) <= continuationRhsBudget;
                // Break before long string-like initializers when the combined line would exceed limits.
                // Restrict to string literals, text blocks, and literal-only "+" chains so array/object
                // initializers are not mis-measured via toString().
                int tailWidth = tailWidthAfterEqualsForInitializerBreakHeuristic(init);
                boolean longStringLikeInitializer = initializerNeedsForcedBreakBeforeChunking(init);
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

    @Override
    public void visit(LambdaExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        printLambdaParameters(n, arg);
        printer.print(" -> ");
        if (n.getBody() instanceof BlockStmt block) {
            if (block.getStatements() == null || block.getStatements().isEmpty()) {
                // Empty statement list but comments inside the block must stay between { and }, not
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
        if (!isNullOrEmpty(ps) && argumentListFormatter.paramsNeedWrap(ps)) {
            argumentListFormatter.printParametersList(ps, arg);
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
    public void visit(StringLiteralExpr n, Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printComment(n.getComment(), arg);
        int quotedLen = StringEscapeUtils.escapeJava(n.getValue()).length() + 2;
        if (column() + quotedLen > fmt.lineLength() && !isInsideStringConcatChain(n)) {
            emitChunkedStringLiteral(n.getValue());
        } else {
            // Do not call super.visit: DefaultPrettyPrinterVisitor would print orphan + comment again.
            printer.print("\"");
            printer.print(n.getValue());
            printer.print("\"");
        }
        printOrphanCommentsEnding(n);
    }

    private static boolean isInsideStringConcatChain(StringLiteralExpr n) {
        return n.getParentNode()
                .filter(p -> p instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS)
                .isPresent();
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
            argumentListFormatter.printCommaSeparatedExprs(labels, arg);
        }
        entry.getGuard().ifPresent(
                guard -> {
                    int flat = column() + SWITCH_GUARD_KEYWORD_WIDTH + ctx.est(guard); // " when " + guard
                    if (flat <= fmt.lineLength()) {
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

    private static Expression stripParens(Expression e) {
        while (e instanceof EnclosedExpr enc) {
            e = enc.getInner();
        }
        return e;
    }

    private static void collectPlusOperands(Expression e, List<Expression> out) {
        e = stripParens(e);
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            collectPlusOperands(b.getLeft(), out);
            collectPlusOperands(b.getRight(), out);
        } else {
            out.add(e);
        }
    }

    private static boolean isStringConcatChainLeaf(Expression e) {
        return e instanceof StringLiteralExpr
                || e instanceof CharLiteralExpr
                || e instanceof TextBlockLiteralExpr;
    }

    /**
     * True for {@code "a" + "b"}, parenthesized variants, and char/string/text-block operands only
     * (no identifiers or calls).
     */
    private static boolean isTopLevelStringConcatChain(Expression init) {
        Expression root = stripParens(init);
        if (!(root instanceof BinaryExpr b) || b.getOperator() != BinaryExpr.Operator.PLUS) {
            return false;
        }
        List<Expression> parts = new ArrayList<>();
        collectPlusOperands(b, parts);
        if (parts.size() < 2) {
            return false;
        }
        for (Expression p : parts) {
            if (!isStringConcatChainLeaf(p)) {
                return false;
            }
        }
        return true;
    }

    /** Single-line width estimate (line breaks treated as spaces) for line-budget heuristics. */
    private static int flatExprSourceWidth(Expression e) {
        return e.toString().replaceAll("\\R", " ").length();
    }

    /**
     * When non-negative, adding this to the column after printing {@code "="} is compared to line
     * limits for break-before-initializer layout.
     */
    private static int tailWidthAfterEqualsForInitializerBreakHeuristic(Expression init) {
        Expression stripped = stripParens(init);
        if (stripped instanceof StringLiteralExpr sl) {
            return 1 + StringEscapeUtils.escapeJava(sl.getValue()).length() + 2;
        }
        if (stripped instanceof TextBlockLiteralExpr) {
            return 1 + flatExprSourceWidth(stripped);
        }
        if (stripped instanceof LambdaExpr) {
            return 1 + flatExprSourceWidth(stripped);
        }
        if (stripped instanceof BinaryExpr b
                && b.getOperator() == BinaryExpr.Operator.PLUS
                && isTopLevelStringConcatChain(stripped)) {
            // AST toString() is far shorter than real source for huge literal-only + trees; use the same
            // merged-string width as a single quoted literal so we break after "=" on the same pass as
            // visit(StringLiteralExpr), keeping chunk budgets (and idempotency) stable.
            return initializerTailWidthForMergedStringConcatLiterals(stripped);
        }
        return -1;
    }

    /**
     * One-line width estimate for {@code = init} when {@code init} is a literal-only {@code +} chain:
     * same as one double-quoted literal with merged {@link StringLiteralExpr} values (escaped).
     */
    private static int initializerTailWidthForMergedStringConcatLiterals(Expression stripped) {
        Expression root = stripParens(stripped);
        BinaryExpr b = (BinaryExpr) root;
        List<Expression> parts = new ArrayList<>();
        collectPlusOperands(b, parts);
        StringBuilder merged = new StringBuilder();
        for (Expression part : parts) {
            Expression leaf = stripParens(part);
            if (leaf instanceof StringLiteralExpr sl) {
                merged.append(sl.getValue());
            } else {
                return 1 + flatExprSourceWidth(stripped);
            }
        }
        return 1 + StringEscapeUtils.escapeJava(merged.toString()).length() + 2;
    }

    private static boolean initializerNeedsForcedBreakBeforeChunking(Expression init) {
        if (init instanceof StringLiteralExpr sl) {
            return sl.getValue().length() >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
        }
        return isTopLevelStringConcatChain(init)
                && mergedStringLiteralChainCharCount(init) >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
    }

    private static int mergedStringLiteralChainCharCount(Expression init) {
        Expression root = stripParens(init);
        if (!(root instanceof BinaryExpr b) || b.getOperator() != BinaryExpr.Operator.PLUS) {
            return 0;
        }
        List<Expression> parts = new ArrayList<>();
        collectPlusOperands(b, parts);
        int n = 0;
        for (Expression part : parts) {
            Expression leaf = stripParens(part);
            if (leaf instanceof StringLiteralExpr sl) {
                n += sl.getValue().length();
            } else {
                return 0;
            }
        }
        return n;
    }

}
