package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments;
import com.github.javaparser.printer.SourcePrinter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;

import java.util.Optional;

/**
 * Shared printing context passed to all delegate formatters. Wraps the output printer, formatter
 * configuration, and visitor dispatch so that delegates can print and recurse without depending on
 * the visitor class directly.
 */
@SuppressWarnings("VoidUsed")
record LayoutContext(FormatterConfig fmt, SourcePrinter printer, PrincePrettyPrinterVisitor visitor) {

    /** Returns the active formatter configuration. */
    FormatterConfig config() {
        return fmt;
    }

    // ── output primitives ─────────────────────────────────────────────────────

    /** Writes text to the output stream without a newline. */
    void print(String text) {
        printer.print(text);
    }

    /** Writes a newline to the output stream. */
    void println() {
        printer.println();
    }

    /** Returns the current output column. */
    int column() {
        return printer.getCursor().column;
    }

    // ── indentation ───────────────────────────────────────────────────────────

    /** Increases indentation level by one unit. */
    void indent() {
        printer.indent();
    }

    /** Decreases indentation level by one unit. */
    void unindent() {
        printer.unindent();
    }

    /** Aligns indentation to a target column with a safe fallback. */
    void indentWithAlignToSafe(int targetColumn) {
        try {
            printer.indentWithAlignTo(targetColumn);
        } catch (IllegalStateException ex) {
            printer.indent();
        }
    }

    /**
     * Pads the current line with spaces until the cursor column reaches at least {@code targetColumn}
     * (0-based, per {@link com.github.javaparser.Position#getColumn}). Prefer this over
     * {@code indentWithAlignTo} when the printer is already inside a nested {@code indent()} scope
     * and alignment would otherwise throw or fall back to a one-level {@code indent()}.
     */
    void padToColumn0(int targetColumn) {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            indentWithAlignToSafe(targetColumn);
            return;
        }
        int c = column();
        if (targetColumn > c) {
            print(" ".repeat(targetColumn - c));
        }
    }

    /**
     * R3: one continuation-indent step (always {@code 2 * indentSize} — see
     * {@link FormatterConfig#continuationIndentSize()}), used for wrapped parameters, ternary arms,
     * for-header lines, and similar (not for method-chain segments; use {@link #printChainIndent}).
     */
    void printCont() {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            printer.print("\t".repeat(fmt.continuationIndentSize()));
        } else {
            printer.print(" ".repeat(fmt.continuationIndentSize()));
        }
    }

    /**
     * Prints a single block-indent unit, used as the chain-continuation indent.
     *
     * <p>Method chains are visually self-delimiting (each wrapped segment starts with {@code .}),
     * so they only need one indent level to remain readable instead of the {@code 2 * indentSize}
     * used for delimited list continuations. See {@code docs/canonical-formatting-rules.md} Rule 7
     * and TDR-015.
     */
    void printChainIndent() {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            printer.print("\t".repeat(fmt.indentSize()));
        } else {
            printer.print(" ".repeat(fmt.indentSize()));
        }
    }

    /** Prints extra indentation used by narrow wrapped lists. */
    void printNarrowListIndent() {
        if (fmt.indentStyle() == IndentStyle.TABS) {
            printer.print("\t".repeat(fmt.continuationIndentSize() * 2));
        } else {
            printer.print(" ".repeat(fmt.continuationIndentSize() * 2));
        }
    }

    // ── width estimation ──────────────────────────────────────────────────────

    /** Returns whether adding width would exceed configured line length. */
    boolean wouldExceedLineLength(int additionalWidth) {
        return column() + additionalWidth > fmt.lineLength();
    }

    // ── visitor dispatch ──────────────────────────────────────────────────────

    /** Delegates node formatting back through the visitor. */
    void accept(Node node, Void arg) {
        node.accept(visitor, arg);
    }

    /**
     * Formats the node using the default (superclass) visitor, bypassing any custom overrides.
     * Used as a fallback when the custom formatting logic does not apply.
     */
    void acceptDefault(Node node, Void arg) {
        visitor.defaultVisit(node, arg);
    }

    // ── comment handling ──────────────────────────────────────────────────────

    /** Emits a node-associated comment using visitor rules. */
    void printComment(Optional<Comment> comment, Void arg) {
        visitor.doPrintComment(comment, arg);
    }

    /** Emits orphan comments that appear before a given child node. */
    void printOrphanCommentsBeforeThisChildNode(Node node) {
        visitor.printOrphanCommentsBeforeThisChildNode(node);
    }

    // ── inherited declaration helpers ─────────────────────────────────────────

    /** Delegates modifier printing to visitor inherited helper. */
    void printModifiers(NodeList<?> modifiers) {
        visitor.doPrintModifiers(modifiers);
    }

    /** Delegates member-annotation printing to visitor inherited helper. */
    void printMemberAnnotations(NodeList<AnnotationExpr> annotations, Void arg) {
        visitor.doPrintMemberAnnotations(annotations, arg);
    }

    /** Delegates generic annotation printing to visitor inherited helper. */
    void printAnnotations(NodeList<AnnotationExpr> annotations, boolean lineBreaks, Void arg) {
        visitor.doPrintAnnotations(annotations, lineBreaks, arg);
    }

    /** Delegates type-argument printing to visitor inherited helper. */
    void printTypeArgs(NodeWithTypeArguments<?> node, Void arg) {
        visitor.doPrintTypeArgs(node, arg);
    }

    /** Delegates argument-list printing to the visitor override. */
    <T extends Expression> void printArguments(NodeList<T> args, Void arg) {
        visitor.doPrintArguments(args, arg);
    }

    /** Delegates member printing (blank lines between members) to the visitor override. */
    void printMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        visitor.doPrintMembers(members, arg);
    }

    /** Delegates compact-class member printing to the visitor inherited helper. */
    void printCompactClassMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
        visitor.doPrintCompactClassMembers(members, arg);
    }

    /** Emits trailing orphan comments for a node (closing-brace placement rules). */
    void printOrphanCommentsEnding(Node node) {
        visitor.doPrintOrphanCommentsEnding(node);
    }

    /**
     * Prints a raw Java string value as quoted chunks joined by {@code +}, matching
     * {@link PrincePrettyPrinterVisitor#visit(com.github.javaparser.ast.expr.StringLiteralExpr, Void)}
     * for long literals so literal-only {@code +} chains round-trip idempotently.
     */
    void emitChunkedStringLiteral(String raw) {
        visitor.emitChunkedStringLiteral(raw);
    }
}
