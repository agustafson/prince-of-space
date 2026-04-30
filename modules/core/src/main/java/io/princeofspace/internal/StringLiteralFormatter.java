package io.princeofspace.internal;

import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.utils.StringEscapeUtils;
import io.princeofspace.model.FormatterConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * String-literal and literal-only concat layout. Keeping this out of the visitor makes the
 * idempotency-sensitive chunking rules easier to reason about in one place.
 */
@SuppressWarnings("VoidUsed")
final class StringLiteralFormatter {
    private static final int BALANCED_PAREN_HEADROOM_MIN = 16;
    private static final int BALANCED_PAREN_HEADROOM_MAX = 64;
    private static final int BALANCED_PAREN_HEADROOM_DIVISOR = 3;
    private static final int SINGLE_ITEM_COUNT = 1;
    private static final int MIN_CONCAT_CHAIN_PARTS = 2;
    private static final int LARGE_STRING_FORCE_BREAK_THRESHOLD = 500;
    private static final int MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS = 128;
    private static final int WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING = 4;

    private final LayoutContext ctx;
    private final FormatterConfig fmt;

    StringLiteralFormatter(LayoutContext ctx) {
        this.ctx = ctx;
        this.fmt = ctx.config();
    }

    // R3 + R4: break before a long message; R1: string chunking must be stable for idempotent re-parses.
    void printAssertMessageRespectingMaxLine(Expression msg, Void arg) {
        if (ctx.column() + WidthMeasurer.flatWidth(msg, fmt) > fmt.lineLength()) {
            ctx.println();
            ctx.printCont();
        }
        if (msg instanceof StringLiteralExpr sl) {
            int quotedLen = StringEscapeUtils.escapeJava(sl.getValue()).length() + 2;
            if (ctx.column() + quotedLen > fmt.lineLength()) {
                emitChunkedStringLiteral(sl.getValue());
                return;
            }
        }
        ctx.accept(msg, arg);
    }

    /**
     * Emits a chain of string literals joined by {@code +} so each physical line stays within
     * {@link FormatterConfig#lineLength()} (assert message must remain a single expression).
     *
     * <p>R4: physical line width; R1: splitting is deterministic (same config + raw text -> same chunks).
     */
    void emitChunkedStringLiteral(String raw) {
        printWrappedStringLiteralChunks(raw);
    }

    // R4 + R1: very large literal-only '+' trees are re-printed without redundant grouping when safe;
    // R10: must keep parens when JavaParser/grammar requires (e.g. scope of call or array access).
    boolean tryFormatLargeStringConcatEnclosed(EnclosedExpr n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        if (isTopLevelStringConcatChain(n.getInner())
                && mergedStringLiteralChainCharCount(n.getInner()) >= LARGE_STRING_FORCE_BREAK_THRESHOLD) {
            Expression inner = n.getInner();
            while (inner instanceof EnclosedExpr enc) {
                inner = enc.getInner();
            }
            if (mustKeepParensAroundConcatForParent(n)) {
                ctx.print("(");
                ctx.accept(inner, arg);
                ctx.print(")");
            } else {
                ctx.accept(inner, arg);
            }
            ctx.printOrphanCommentsEnding(n);
            return true;
        }
        return false;
    }

    // R4: if a standalone literal is too long, emit '+'-joined chunks; skip when already inside a
    // concat chain (parent BinaryExpr+ handles the tree). R1: do not use super — it reprints comments.
    void formatStringLiteral(StringLiteralExpr n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        int anchorColumn = ctx.column();
        if (isLeadingCommentInsideArrayInitializer(n) && n.getComment().orElse(null) instanceof BlockComment blockComment) {
            printNormalizedLeadingBlockComment(blockComment, anchorColumn);
        } else {
            ctx.printComment(n.getComment(), arg);
        }
        if (isLeadingCommentInsideArrayInitializer(n)) {
            ctx.padToColumn0(anchorColumn);
        }
        int quotedLen = StringEscapeUtils.escapeJava(n.getValue()).length() + 2;
        int lineLen = fmt.lineLength();
        boolean shouldChunk = !isInsideStringConcatChain(n) && ctx.column() + quotedLen > lineLen;
        if (shouldChunk) {
            emitChunkedStringLiteral(n.getValue());
        } else {
            // Do not call the default visitor: it would print orphan + comment again.
            ctx.print("\"");
            ctx.print(n.getValue());
            ctx.print("\"");
        }
        ctx.printOrphanCommentsEnding(n);
    }

    private static boolean isLeadingCommentInsideArrayInitializer(StringLiteralExpr n) {
        if (n.getComment().isEmpty()
                || n.getComment().get().getRange().isEmpty()
                || n.getRange().isEmpty()) {
            return false;
        }
        if (n.getComment().get().getRange().get().begin.line >= n.getRange().get().begin.line) {
            return false;
        }
        return n.getParentNode().isPresent() && n.getParentNode().get() instanceof ArrayInitializerExpr;
    }

    private void printNormalizedLeadingBlockComment(BlockComment blockComment, int anchorColumn) {
        String[] lines = blockComment.getContent().split("\\R", -1);
        ctx.print("/*");
        ctx.println();
        boolean printedContentLine = false;
        boolean pendingBlank = false;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty()) {
                if (printedContentLine) {
                    pendingBlank = true;
                }
                continue;
            }
            if (pendingBlank) {
                ctx.padToColumn0(anchorColumn);
                ctx.print(" *");
                ctx.println();
                pendingBlank = false;
            }
            ctx.padToColumn0(anchorColumn);
            if (trimmed.startsWith("*")) {
                ctx.print(" " + trimmed);
            } else {
                ctx.print(" * " + trimmed);
            }
            ctx.println();
            printedContentLine = true;
        }
        if (!printedContentLine) {
            ctx.padToColumn0(anchorColumn);
            ctx.print(" *");
            ctx.println();
        }
        ctx.padToColumn0(anchorColumn);
        ctx.print(" */");
        ctx.println();
    }

    /**
     * When non-negative, adding this to the column after printing {@code "="} is compared to line
     * limits for break-before-initializer layout.
     */
    int tailWidthAfterEqualsForInitializerBreakHeuristic(Expression init) {
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

    boolean initializerNeedsForcedBreakBeforeChunking(Expression init) {
        if (init instanceof StringLiteralExpr sl) {
            return sl.getValue().length() >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
        }
        return isTopLevelStringConcatChain(init)
                && mergedStringLiteralChainCharCount(init) >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
    }

    private void printWrappedStringLiteralChunks(String raw) {
        // R1: subsequent piece boundaries depend only on (raw, config) so a re-parse of "a"+"b"+...
        // splits identically (each operand is preserved as-is by visit(StringLiteralExpr) inside a
        // + chain). The FIRST piece is sized to the current column so it can stay on the same line as
        // the prefix (e.g. throw new T("...") chunked inline).
        List<String> pieces = collectRawStringPiecesForChunking(raw, columnAwareFirstPieceMaxRoom());
        if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            emitLinearStringPiecesFromList(pieces);
        } else {
            emitBalancedParenStringPieces(pieces);
        }
    }

    private int columnAwareFirstPieceMaxRoom() {
        return Math.max(1, fmt.lineLength() - ctx.column() - 2);
    }

    private int stableMaxRoomAfterPlusPrefix() {
        int openingQuoteColumn =
                WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING * fmt.indentSize()
                        + fmt.continuationIndentSize()
                        + 2;
        int maxRoom = fmt.lineLength() - openingQuoteColumn - 2;
        return Math.max(maxRoom, 1);
    }

    private int balancedStringConcatParenHeadroom() {
        // Reserve roughly one-third of line length, but keep a practical floor/ceiling.
        return Math.max(
                BALANCED_PAREN_HEADROOM_MIN,
                Math.min(BALANCED_PAREN_HEADROOM_MAX, fmt.lineLength() / BALANCED_PAREN_HEADROOM_DIVISOR));
    }

    private List<String> collectRawStringPiecesForChunking(String raw, int firstPieceMaxRoom) {
        int baseMax = stableMaxRoomAfterPlusPrefix();
        int firstMax = Math.max(1, Math.min(baseMax, firstPieceMaxRoom));
        List<String> pieces = splitRawIntoStringPieces(raw, firstMax, baseMax);
        if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            return pieces;
        }
        int headroom = balancedStringConcatParenHeadroom();
        int restBudget = Math.max(1, baseMax - headroom);
        int firstBudget = Math.max(1, Math.min(firstMax, restBudget));
        return splitRawIntoStringPieces(raw, firstBudget, restBudget);
    }

    private static List<String> splitRawIntoStringPieces(String raw, int firstMaxRoom, int restMaxRoom) {
        List<String> pieces = new ArrayList<>();
        if (raw.isEmpty()) {
            pieces.add("");
            return pieces;
        }
        int i = 0;
        boolean first = true;
        while (i < raw.length()) {
            int useMax = first ? firstMaxRoom : restMaxRoom;
            int grow = growPieceEndIndexForChunking(raw, i, useMax);
            pieces.add(raw.substring(i, grow));
            i = grow;
            first = false;
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
        // R1: when the entire remainder fits in maxRoom, do not backtrack to a preferred break.
        if (grow == raw.length()) {
            return grow;
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
        if (pieces.isEmpty()) {
            return;
        }
        int firstLen = StringEscapeUtils.escapeJava(pieces.get(0)).length() + 2;
        int lineLen = fmt.lineLength();
        // R4 safety net: column-aware first-piece sizing in collectRawStringPiecesForChunking should
        // make this branch unreachable for chunk-driven callers; keep the guard for any caller that
        // pre-built pieces without column awareness so a long first chunk still gets a continuation.
        if (ctx.column() + firstLen > lineLen) {
            ctx.println();
            ctx.printCont();
        }
        for (int idx = 0; idx < pieces.size(); idx++) {
            if (idx > 0) {
                ctx.println();
                ctx.printCont();
                ctx.print("+ ");
            }
            String piece = pieces.get(idx);
            ctx.print("\"");
            ctx.print(StringEscapeUtils.escapeJava(piece));
            ctx.print("\"");
        }
    }

    private void emitBalancedParenStringPieces(List<String> pieces) {
        emitBalancedParenStringPiecesImpl(pieces, 0, pieces.size(), true);
    }

    private void emitBalancedParenStringPiecesImpl(List<String> pieces, int lo, int hi, boolean isGlobalFirstLeaf) {
        if (hi - lo == SINGLE_ITEM_COUNT) {
            printOneStringLiteralPiece(pieces.get(lo), isGlobalFirstLeaf);
            return;
        }
        int mid = (lo + hi) >>> 1;
        ctx.print("(");
        emitBalancedParenStringPiecesImpl(pieces, lo, mid, isGlobalFirstLeaf);
        ctx.print(" + ");
        // Right operand follows an explicit infix "+"; do not emit the continuation "+ " prefix used
        // between top-level fragments (would parse as string + unary-plus and break idempotency).
        emitBalancedParenStringPiecesImpl(pieces, mid, hi, true);
        ctx.print(")");
    }

    private void printOneStringLiteralPiece(String piece, boolean isGlobalFirstLeaf) {
        if (!isGlobalFirstLeaf) {
            ctx.println();
            ctx.printCont();
            ctx.print("+ ");
        }
        ctx.print("\"");
        ctx.print(StringEscapeUtils.escapeJava(piece));
        ctx.print("\"");
    }

    private static boolean mustKeepParensAroundConcatForParent(EnclosedExpr n) {
        return n.getParentNode()
                .map(
                        p ->
                                (p instanceof MethodCallExpr mc && mc.getScope().map(s -> Objects.equals(s, n)).orElse(false))
                                        || (p instanceof ArrayAccessExpr aa && Objects.equals(aa.getName(), n)))
                .orElse(false);
    }

    private static boolean isInsideStringConcatChain(StringLiteralExpr n) {
        return n.getParentNode()
                .filter(p -> p instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS)
                .isPresent();
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
        if (parts.size() < MIN_CONCAT_CHAIN_PARTS) {
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
