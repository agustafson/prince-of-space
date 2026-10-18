package io.princeofspace.internal;

import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.utils.StringEscapeUtils;
import io.princeofspace.model.FormatterConfig;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

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
    private static final HexFormat UPPERCASE_HEX = HexFormat.of().withUpperCase();
    /**
     * Upper bound on nested block depth assumed when budgeting chunk columns — deeper nesting than this
     * may slightly underestimate remaining width (conservative for wrapping decisions).
     */
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
            String value = semanticValue(sl);
            int quotedLen = escapeJava(value).length() + 2;
            if (ctx.column() + quotedLen > fmt.lineLength()) {
                emitChunkedStringLiteral(value);
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

    // R4 + R1: remove an enclosing pair from large literal-only '+' chains unless the parent needs
    // it for expression binding or it anchors the balanced tree that keeps very large chains stable.
    boolean tryFormatLargeStringConcatEnclosed(EnclosedExpr n, Void arg) {
        if (isTopLevelStringConcatChain(n.getInner())
                && mergedStringLiteralChainCharCount(n.getInner()) >= LARGE_STRING_FORCE_BREAK_THRESHOLD) {
            ctx.printOrphanCommentsBeforeThisChildNode(n);
            ctx.printComment(n.getComment(), arg);
            Expression inner = n.getInner();
            while (inner instanceof EnclosedExpr enc) {
                inner = enc.getInner();
            }
            boolean needsParens =
                    needsParensAroundGeneratedConcat(n)
                            || stringConcatPartCount(inner) > MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS;
            if (needsParens) {
                ctx.print("(");
            }
            ctx.accept(inner, arg);
            if (needsParens) {
                ctx.print(")");
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
            ctx.padToColumn(anchorColumn);
        }
        String sourceValue = escapeUnpairedSurrogates(n.getValue());
        int quotedLen = sourceValue.length() + 2;
        int lineLen = fmt.lineLength();
        boolean shouldChunk = !isInsideStringConcatChain(n) && ctx.column() + quotedLen > lineLen;
        if (shouldChunk) {
            boolean needsParens = needsParensAroundGeneratedConcat(n);
            printWrappedStringLiteralChunks(
                    semanticValue(n), needsParens, generatedConcatTrailingWidth(n, needsParens));
        } else {
            // Do not call the default visitor: it would print orphan + comment again.
            ctx.print("\"");
            ctx.print(sourceValue);
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
        ctx.printNormalizedBlockComment(blockComment, anchorColumn);
    }

    /**
     * When non-negative, adding this to the column after printing {@code "="} is compared to line
     * limits for break-before-initializer layout.
     */
    int tailWidthAfterEqualsForInitializerBreakHeuristic(Expression init) {
        Expression stripped = stripParens(init);
        if (stripped instanceof StringLiteralExpr sl) {
            return 1 + escapeJava(semanticValue(sl)).length() + 2;
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
            return semanticValue(sl).length() >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
        }
        return isTopLevelStringConcatChain(init)
                && mergedStringLiteralChainCharCount(init) >= LARGE_STRING_FORCE_BREAK_THRESHOLD;
    }

    private void printWrappedStringLiteralChunks(String raw) {
        printWrappedStringLiteralChunks(raw, false, 0);
    }

    private void printWrappedStringLiteralChunks(String raw, boolean needsParens, int trailingWidth) {
        // R1: subsequent piece boundaries depend only on (raw, config) so a re-parse of "a"+"b"+...
        // splits identically (each operand is preserved as-is by visit(StringLiteralExpr) inside a
        // + chain). The FIRST piece is sized to the current column so it can stay on the same line as
        // the prefix (e.g. throw new T("...") chunked inline).
        List<String> pieces =
                collectRawStringPiecesForChunking(
                        raw,
                        columnAwareFirstPieceMaxRoom(needsParens),
                        needsParens,
                        trailingWidth);
        if (pieces.size() == SINGLE_ITEM_COUNT) {
            emitLinearStringPiecesFromList(pieces);
        } else if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            if (needsParens) {
                ctx.print("(");
            }
            emitLinearStringPiecesFromList(pieces);
            if (needsParens) {
                ctx.print(")");
            }
        } else {
            emitBalancedParenStringPieces(pieces);
        }
    }

    private int columnAwareFirstPieceMaxRoom(boolean needsParens) {
        return Math.max(
                1,
                fmt.lineLength()
                        - ctx.column()
                        - 2
                        - (needsParens ? 1 : 0));
    }

    private int stableMaxRoomAfterPlusPrefix(boolean needsParens) {
        int openingQuoteColumn =
                WORST_CASE_BLOCK_INDENTS_FOR_STRING_CHUNKING * fmt.indentSize()
                        + fmt.continuationIndentSize()
                        + 2;
        int maxRoom =
                fmt.lineLength()
                        - openingQuoteColumn
                        - 2
                        - (needsParens ? 1 : 0);
        return Math.max(maxRoom, 1);
    }

    private int balancedStringConcatParenHeadroom() {
        // Reserve roughly one-third of line length, but keep a practical floor/ceiling.
        return Math.max(
                BALANCED_PAREN_HEADROOM_MIN,
                Math.min(BALANCED_PAREN_HEADROOM_MAX, fmt.lineLength() / BALANCED_PAREN_HEADROOM_DIVISOR));
    }

    private List<String> collectRawStringPiecesForChunking(
            String raw, int firstPieceMaxRoom, boolean needsParens, int trailingWidth) {
        int baseMax = stableMaxRoomAfterPlusPrefix(needsParens);
        int firstMax = Math.max(1, Math.min(baseMax, firstPieceMaxRoom));
        List<String> pieces = splitRawIntoStringPieces(raw, firstMax, baseMax);
        reserveTrailingWidthInLastPiece(
                pieces, pieces.size() == SINGLE_ITEM_COUNT ? firstMax : baseMax, trailingWidth);
        if (pieces.size() <= MAX_SHALLOW_LINEAR_STRING_CONCAT_PARTS) {
            return pieces;
        }
        int headroom = balancedStringConcatParenHeadroom();
        int restBudget = Math.max(1, baseMax - headroom);
        int firstBudget = Math.max(1, Math.min(firstMax, restBudget));
        pieces = splitRawIntoStringPieces(raw, firstBudget, restBudget);
        reserveTrailingWidthInLastPiece(
                pieces, pieces.size() == SINGLE_ITEM_COUNT ? firstBudget : restBudget, trailingWidth);
        return pieces;
    }

    private static void reserveTrailingWidthInLastPiece(
            List<String> pieces, int lastPieceMaxRoom, int trailingWidth) {
        if (trailingWidth == 0) {
            return;
        }
        int lastIndex = pieces.size() - 1;
        List<String> splitLast =
                splitRawIntoStringPieces(
                        pieces.get(lastIndex),
                        Math.max(1, lastPieceMaxRoom - trailingWidth),
                        Math.max(1, lastPieceMaxRoom - trailingWidth));
        if (splitLast.size() == SINGLE_ITEM_COUNT) {
            return;
        }
        pieces.set(lastIndex, String.join("", splitLast.subList(0, splitLast.size() - 1)));
        pieces.add(splitLast.get(splitLast.size() - 1));
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
        int escapedLength = 0;
        int preferredBreak = -1;
        while (grow < raw.length()) {
            int cp = raw.codePointAt(grow);
            int growNext = grow + Character.charCount(cp);
            int nextEscapedLength = escapedLength + escapedCodePointLength(cp);
            if (nextEscapedLength + 2 > maxRoom) {
                break;
            }
            grow = growNext;
            escapedLength = nextEscapedLength;
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
        int firstLen = escapeJava(pieces.get(0)).length() + 2;
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
            ctx.print(escapeJava(piece));
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
        ctx.print(escapeJava(piece));
        ctx.print("\"");
    }

    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private static boolean needsParensAroundGeneratedConcat(Expression expression) {
        return expression.getParentNode()
                .map(
                        parent ->
                                (parent instanceof MethodCallExpr call
                                                && call.getScope().orElse(null) == expression)
                                        || (parent instanceof MethodReferenceExpr reference
                                                && reference.getScope() == expression)
                                        || (parent instanceof FieldAccessExpr access
                                                && access.getScope() == expression)
                                        || (parent instanceof ArrayAccessExpr access
                                                && access.getName() == expression)
                                        || (parent instanceof ObjectCreationExpr creation
                                                && creation.getScope().orElse(null) == expression)
                                        || (parent instanceof CastExpr cast
                                                && cast.getExpression() == expression))
                .orElse(false);
    }

    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private int generatedConcatTrailingWidth(Expression expression, boolean needsParens) {
        if (!needsParens) {
            return 0;
        }
        int trailingWidth = 0;
        Expression child = expression;
        while (child.getParentNode().isPresent()) {
            var parent = child.getParentNode().get();
            if (parent instanceof FieldAccessExpr access && access.getScope() == child) {
                trailingWidth += 1 + access.getNameAsString().length();
                child = access;
            } else if (parent instanceof ArrayAccessExpr access && access.getName() == child) {
                trailingWidth += 2 + WidthMeasurer.flatWidth(access.getIndex(), fmt);
                child = access;
            } else if (parent instanceof ObjectCreationExpr creation
                    && creation.getScope().orElse(null) == child) {
                trailingWidth += WidthMeasurer.objectCreationScopeSuffixWidth(creation);
                child = creation;
            } else if (parent instanceof MethodReferenceExpr reference
                    && reference.getScope() == child) {
                return trailingWidth
                        + WidthMeasurer.flatWidth(reference, fmt)
                        - WidthMeasurer.flatWidth(child, fmt);
            } else {
                break;
            }
        }
        return trailingWidth;
    }

    private static int stringConcatPartCount(Expression expression) {
        List<Expression> parts = new ArrayList<>();
        collectPlusOperands(expression, parts);
        return parts.size();
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
                merged.append(semanticValue(sl));
            } else {
                return 1 + flatExprSourceWidth(stripped);
            }
        }
        return 1 + escapeJava(merged.toString()).length() + 2;
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
                n += semanticValue(sl).length();
            } else {
                return 0;
            }
        }
        return n;
    }

    static String semanticValue(StringLiteralExpr literal) {
        return literal.getValue().translateEscapes();
    }

    private static int escapedCodePointLength(int codePoint) {
        if (codePoint > Character.MAX_VALUE) {
            return Character.charCount(codePoint);
        }
        return switch (codePoint) {
            case '"', '\\', '\b', '\n', '\t', '\f', '\r' -> 2;
            default -> Character.isSurrogate((char) codePoint) ? "\\u0000".length() : 1;
        };
    }

    private static String escapeJava(String value) {
        return escapeUnpairedSurrogates(StringEscapeUtils.escapeJava(value));
    }

    private static String escapeUnpairedSurrogates(String value) {
        StringBuilder escaped = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)
                    && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                if (escaped != null) {
                    escaped.append(ch).append(value.charAt(++i));
                } else {
                    i++;
                }
            } else if (Character.isSurrogate(ch)) {
                if (escaped == null) {
                    escaped =
                            new StringBuilder(value.length() + "\\u0000".length())
                                    .append(value, 0, i);
                }
                escaped.append("\\u").append(UPPERCASE_HEX.toHexDigits(ch));
            } else if (escaped != null) {
                escaped.append(ch);
            }
        }
        return escaped == null ? value : escaped.toString();
    }
}
