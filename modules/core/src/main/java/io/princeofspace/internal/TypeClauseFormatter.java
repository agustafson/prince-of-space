package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.UnionType;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;

import java.util.Iterator;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Formats {@code extends}/{@code implements}/{@code permits} type lists, {@code throws} clauses,
 * and union types ({@code |}) with the same width and wrap-style rules as the main visitor.
 */
final class TypeClauseFormatter {
    private static final int CLAUSE_SEPARATOR_WIDTH = 2; // ", "
    private static final int INLINE_EXTENDS_KEYWORD_WIDTH = 8; // " extends"
    private static final int INLINE_IMPLEMENTS_KEYWORD_WIDTH = 12; // " implements"
    private static final int INLINE_PERMITS_KEYWORD_WIDTH = 9; // " permits "
    private static final int INLINE_THROWS_KEYWORD_WIDTH = 7; // " throws "
    private static final int UNION_OPERATOR_WITH_SPACES_WIDTH = 3; // " | "
    private static final int GREEDY_LIST_TRAILING_HEADROOM = 2;

    private final LayoutContext ctx;
    private final FormatterConfig fmt;
    private final CommentUtils commentUtils;

    TypeClauseFormatter(LayoutContext ctx, FormatterConfig fmt, CommentUtils commentUtils) {
        this.ctx = ctx;
        this.fmt = fmt;
        this.commentUtils = commentUtils;
    }

    /** Estimates the width of a comma-separated list of class/interface type names (flat text). */
    int implementsTypesWidth(NodeList<ClassOrInterfaceType> types) {
        int w = 0;
        boolean first = true;
        for (ClassOrInterfaceType t : types) {
            if (!first) {
                w += CLAUSE_SEPARATOR_WIDTH;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    /**
     * Prints an {@code extends} clause, possibly wrapping.
     *
     * @return true if the clause wrapped to a new line
     */
    boolean printExtendsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.isEmpty()) {
            return false;
        }
        int header = ctx.column();
        int inlineWidth =
                header
                        + INLINE_EXTENDS_KEYWORD_WIDTH
                        + implementsTypesWidth(types)
                        + GREEDY_LIST_TRAILING_HEADROOM;
        if (inlineWidth <= fmt.lineLength()) {
            ctx.print(" extends");
            printInlineTypeClauseList(types, arg);
            return false;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            ctx.println();
            ctx.printCont();
            ctx.print("extends ");
            printTypeListGreedy(types, arg);
            return true;
        }
        if (fmt.wrapStyle() == WrapStyle.BALANCED) {
            ctx.println();
            ctx.printCont();
            ctx.print("extends ");
            ctx.accept(types.get(0), arg);
            for (int i = 1; i < types.size(); i++) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
                ctx.accept(types.get(i), arg);
            }
            return true;
        }
        ctx.println();
        ctx.printCont();
        ctx.print("extends");
        for (int i = 0; i < types.size(); i++) {
            ctx.println();
            ctx.printNarrowListIndent();
            ctx.accept(types.get(i), arg);
            if (i < types.size() - 1) {
                ctx.print(",");
            }
        }
        return true;
    }

    /**
     * Prints an {@code implements} clause, possibly wrapping.
     *
     * @return true if the clause wrapped to a new line
     */
    boolean printImplementsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        int header = ctx.column();
        // Check if everything fits on the current line (include " {" trailing)
        int inlineWidth =
                header
                        + INLINE_IMPLEMENTS_KEYWORD_WIDTH
                        + implementsTypesWidth(types)
                        + GREEDY_LIST_TRAILING_HEADROOM;
        if (inlineWidth <= fmt.lineLength()) {
            ctx.print(" implements");
            printInlineTypeClauseList(types, arg);
            return false;
        }
        // Wrapping needed
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            ctx.println();
            ctx.printCont();
            ctx.print("implements ");
            printTypeListGreedy(types, arg);
            return true;
        }
        if (fmt.wrapStyle() == WrapStyle.BALANCED) {
            ctx.println();
            ctx.printCont();
            ctx.print("implements ");
            ctx.accept(types.get(0), arg);
            for (int i = 1; i < types.size(); i++) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
                ctx.accept(types.get(i), arg);
            }
            return true;
        }
        // NARROW: implements keyword alone, types double-indented
        ctx.println();
        ctx.printCont();
        ctx.print("implements");
        for (int i = 0; i < types.size(); i++) {
            ctx.println();
            ctx.printNarrowListIndent();
            ctx.accept(types.get(i), arg);
            if (i < types.size() - 1) {
                ctx.print(",");
            }
        }
        return true;
    }

    /**
     * Prints a {@code permits} clause, possibly wrapping.
     *
     * @return true if the clause wrapped to a new line
     */
    boolean printPermitsClause(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.isEmpty()) {
            return false;
        }
        int header = ctx.column();
        int permitsInline = header + INLINE_PERMITS_KEYWORD_WIDTH + implementsTypesWidth(types);
        if (fmt.wrapStyle() != WrapStyle.NARROW
                && permitsInline <= fmt.lineLength()) {
            ctx.print(" permits");
            printInlineTypeClauseList(types, arg);
            return false;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            ctx.println();
            ctx.printCont();
            ctx.print("permits ");
            printTypeListGreedy(types, arg);
            return true;
        }
        ctx.println();
        ctx.printCont();
        ctx.print("permits ");
        ctx.accept(types.get(0), arg);
        for (int i = 1; i < types.size(); i++) {
            ctx.print(",");
            ctx.println();
            if (fmt.wrapStyle() == WrapStyle.BALANCED) {
                ctx.printCont();
            } else {
                ctx.printNarrowListIndent();
            }
            ctx.accept(types.get(i), arg);
        }
        return true;
    }

    /** Greedy comma-wrapped printing of class/interface types (WIDE throws/implements-style). */
    void printTypeListGreedy(NodeList<ClassOrInterfaceType> types, Void arg) {
        int budget = fmt.lineLength() - GREEDY_LIST_TRAILING_HEADROOM;
        boolean first = true;
        for (ClassOrInterfaceType t : types) {
            int need = t.toString().length() + (first ? 0 : 2);
            if (!first && (ctx.column() + need > budget || ctx.wouldExceedLineLength(need))) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
            } else if (!first) {
                ctx.print(", ");
            }
            ctx.accept(t, arg);
            first = false;
        }
    }

    void printTypeListInline(NodeList<ClassOrInterfaceType> types, Void arg) {
        for (Iterator<ClassOrInterfaceType> i = types.iterator(); i.hasNext(); ) {
            ctx.accept(i.next(), arg);
            if (i.hasNext()) {
                ctx.print(", ");
            }
        }
    }

    /**
     * Prints a leading space plus comma-separated types, or breaks before a single type when a
     * leading line/block comment forces it.
     */
    void printInlineTypeClauseList(NodeList<ClassOrInterfaceType> types, Void arg) {
        if (types.size() == 1 && commentUtils.hasLeadingLineOrBlockComment(types.get(0))) {
            ctx.println();
            ctx.printCont();
            ctx.accept(types.get(0), arg);
            return;
        }
        ctx.print(" ");
        printTypeListInline(types, arg);
    }

    /** Flat width estimate for a comma-separated reference-type list (e.g. {@code throws}). */
    int referenceTypesFlatWidth(NodeList<ReferenceType> types) {
        int w = 0;
        boolean first = true;
        for (ReferenceType t : types) {
            if (!first) {
                w += CLAUSE_SEPARATOR_WIDTH;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    /** Prints a {@code throws} clause with WIDE/BALANCED/NARROW layout rules. */
    void printThrowsClause(NodeList<ReferenceType> types, Void arg) {
        if (isNullOrEmpty(types)) {
            return;
        }
        int inline = ctx.column() + INLINE_THROWS_KEYWORD_WIDTH + referenceTypesFlatWidth(types);
        if (inline <= fmt.lineLength()) {
            ctx.print(" throws ");
            for (Iterator<ReferenceType> i = types.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.NARROW) {
            ctx.println();
            ctx.printCont();
            ctx.print("throws");
            for (int i = 0; i < types.size(); i++) {
                ctx.println();
                ctx.printNarrowListIndent();
                ctx.accept(types.get(i), arg);
                if (i < types.size() - 1) {
                    ctx.print(",");
                }
            }
            return;
        }
        ctx.println();
        ctx.printCont();
        ctx.print("throws ");
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printReferenceTypeListGreedy(types, arg);
        } else {
            ctx.accept(types.get(0), arg);
            for (int i = 1; i < types.size(); i++) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
                ctx.accept(types.get(i), arg);
            }
        }
    }

    void printReferenceTypeListGreedy(NodeList<ReferenceType> types, Void arg) {
        int budget = fmt.lineLength() - GREEDY_LIST_TRAILING_HEADROOM;
        boolean first = true;
        for (ReferenceType t : types) {
            int need = t.toString().length() + (first ? 0 : 2);
            if (!first && (ctx.column() + need > budget || ctx.wouldExceedLineLength(need))) {
                ctx.print(",");
                ctx.println();
                ctx.printCont();
            } else if (!first) {
                ctx.print(", ");
            }
            ctx.accept(t, arg);
            first = false;
        }
    }

    /** Flat width for a {@code |}-separated union (spaces around {@code |}). */
    int referenceTypesUnionFlatWidth(NodeList<ReferenceType> types) {
        int w = 0;
        boolean first = true;
        for (ReferenceType t : types) {
            if (!first) {
                w += UNION_OPERATOR_WITH_SPACES_WIDTH;
            }
            first = false;
            w += t.toString().length();
        }
        return w;
    }

    void printUnionTypeListGreedy(NodeList<ReferenceType> types, Void arg) {
        int budget = fmt.lineLength() - GREEDY_LIST_TRAILING_HEADROOM;
        boolean first = true;
        for (ReferenceType t : types) {
            int need = t.toString().length() + (first ? 0 : UNION_OPERATOR_WITH_SPACES_WIDTH);
            if (!first && (ctx.column() + need > budget || ctx.wouldExceedLineLength(need))) {
                ctx.println();
                ctx.printCont();
                ctx.print("| ");
            } else if (!first) {
                ctx.print(" | ");
            }
            ctx.accept(t, arg);
            first = false;
        }
    }

    /**
     * Formats the {@code |}-separated elements of a union type after comments and annotations have
     * been printed.
     */
    void formatUnionType(UnionType n, Void arg) {
        NodeList<ReferenceType> types = n.getElements();
        if (isNullOrEmpty(types)) {
            return;
        }
        int inline = ctx.column() + referenceTypesUnionFlatWidth(types);
        if (inline <= fmt.lineLength()) {
            boolean first = true;
            for (ReferenceType t : types) {
                if (!first) {
                    ctx.print(" | ");
                }
                first = false;
                ctx.accept(t, arg);
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.NARROW) {
            ctx.println();
            ctx.printCont();
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) {
                    ctx.println();
                    ctx.printNarrowListIndent();
                    ctx.print("| ");
                }
                ctx.accept(types.get(i), arg);
            }
            return;
        }
        if (fmt.wrapStyle() == WrapStyle.WIDE) {
            printUnionTypeListGreedy(types, arg);
            return;
        }
        ctx.accept(types.get(0), arg);
        for (int i = 1; i < types.size(); i++) {
            ctx.println();
            ctx.printCont();
            ctx.print("| ");
            ctx.accept(types.get(i), arg);
        }
    }
}
