package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Formats type and member declarations (constructors, methods, records, classes/interfaces, enums)
 * with the same layout rules as {@link PrincePrettyPrinterVisitor}, using shared context and
 * clause/argument helpers.
 */
final class DeclarationFormatter {
    private static final int JAVA8_RELEASE = 8;
    private static final int ENUM_HEADER_AND_BRACE_WIDTH = 3 + 2; // " { " + " }"/line-end allowance

    private final LayoutContext ctx;
    private final FormatterConfig fmt;
    private final CommentUtils commentUtils;
    private final ArgumentListFormatter argumentListFormatter;
    private final TypeClauseFormatter typeClauseFormatter;

    DeclarationFormatter(
            LayoutContext ctx,
            FormatterConfig fmt,
            CommentUtils commentUtils,
            ArgumentListFormatter argumentListFormatter,
            TypeClauseFormatter typeClauseFormatter) {
        this.ctx = ctx;
        this.fmt = fmt;
        this.commentUtils = commentUtils;
        this.argumentListFormatter = argumentListFormatter;
        this.typeClauseFormatter = typeClauseFormatter;
    }

    /**
     * Annotations that lexically precede modifiers on a method (e.g. {@code @Override} on its own
     * line) are printed with {@code printMemberAnnotations}; this splits them from inline return-type
     * annotations.
     */
    NodeList<AnnotationExpr> declarationAnnotations(MethodDeclaration n) {
        if (n.getModifiers().isEmpty()) {
            return new NodeList<>(n.getAnnotations());
        }
        int firstModifierColumn =
                n.getModifiers().get(0).getRange().map(r -> r.begin.column).orElse(Integer.MAX_VALUE);
        int firstModifierLine =
                n.getModifiers().get(0).getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE);
        NodeList<AnnotationExpr> result = new NodeList<>();
        for (AnnotationExpr annotation : n.getAnnotations()) {
            int annotationColumn = annotation.getRange().map(r -> r.begin.column).orElse(Integer.MIN_VALUE);
            int annotationLine = annotation.getRange().map(r -> r.begin.line).orElse(Integer.MIN_VALUE);
            // Lexical column alone is not enough: after a first format pass, @Override may sit on the
            // line above "public" at the *same* column as "public", so we also treat any annotation
            // starting on a line strictly before the first modifier line as a declaration annotation.
            boolean beforeOnEarlierLine = annotationLine < firstModifierLine;
            boolean beforeOnSameLine =
                    annotationLine == firstModifierLine && annotationColumn < firstModifierColumn;
            if (beforeOnEarlierLine || beforeOnSameLine) {
                result.add(annotation);
            }
        }
        return result;
    }

    /**
     * Return-type annotations that sit after the last modifier token (same line as modifiers or after)
     * are printed immediately before the type name.
     */
    NodeList<AnnotationExpr> inlineReturnTypeAnnotations(MethodDeclaration n) {
        if (n.getModifiers().isEmpty()) {
            return new NodeList<>();
        }
        int lastModifierColumn =
                n.getModifiers()
                        .get(n.getModifiers().size() - 1)
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

    /**
     * Orphan comments that appear before the first member (or enum constant) belong inside the type
     * body. Leaving them for {@link LayoutContext#printOrphanCommentsEnding} places them before the
     * closing brace, which re-parses differently when the source had {@code { // comment} on the
     * opening line.
     */
    void drainOrphanCommentsBeforeFirstBodyElement(
            Node typeDecl,
            NodeList<BodyDeclaration<?>> members,
            @Nullable NodeList<EnumConstantDeclaration> enumEntriesOrNull,
            Void arg) {
        if (typeDecl.getOrphanComments().isEmpty()) {
            return;
        }
        int firstLine = Integer.MAX_VALUE;
        for (BodyDeclaration<?> m : members) {
            firstLine = Math.min(firstLine, m.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE));
        }
        if (enumEntriesOrNull != null) {
            for (EnumConstantDeclaration e : enumEntriesOrNull) {
                firstLine =
                        Math.min(
                                firstLine,
                                e.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE));
            }
        }
        List<Comment> move = new ArrayList<>();
        for (Comment c : new ArrayList<>(typeDecl.getOrphanComments())) {
            if (c.getRange().isEmpty()) {
                continue;
            }
            if (c.getRange().get().begin.line < firstLine) {
                move.add(c);
            }
        }
        move.sort(
                Comparator.comparingInt((Comment c) -> c.getRange().orElseThrow().begin.line)
                        .thenComparingInt(c -> c.getRange().orElseThrow().begin.column));
        for (Comment c : move) {
            c.remove();
            ctx.printComment(Optional.of(c), arg);
        }
    }

    /** Variant when the type body has only {@link BodyDeclaration}s (no enum constants). */
    void drainOrphanCommentsBeforeFirstBodyElement(Node typeDecl, NodeList<BodyDeclaration<?>> members, Void arg) {
        drainOrphanCommentsBeforeFirstBodyElement(typeDecl, members, null, arg);
    }

    /** Prints a constructor declaration including receiver, parameters, {@code throws}, and body. */
    void formatConstructor(ConstructorDeclaration n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        ctx.printMemberAnnotations(n.getAnnotations(), arg);
        ctx.printModifiers(n.getModifiers());
        argumentListFormatter.printTypeParameters(n.getTypeParameters(), arg);
        if (n.isGeneric()) {
            ctx.print(" ");
        }
        ctx.accept(n.getName(), arg);
        ctx.print("(");
        n.getReceiverParameter()
                .ifPresent(
                        rp -> {
                            ctx.accept(rp, arg);
                            if (!isNullOrEmpty(n.getParameters())) {
                                ctx.print(", ");
                            }
                        });
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && argumentListFormatter.paramsNeedWrap(n.getParameters());
        argumentListFormatter.printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            ctx.println();
        }
        ctx.print(")");
        typeClauseFormatter.printThrowsClause(n.getThrownExceptions(), arg);
        ctx.print(" ");
        ctx.accept(n.getBody(), arg);
    }

    /**
     * Prints a method declaration, splitting declaration vs inline return-type annotations and
     * handling abstract vs compact empty bodies.
     */
    void formatMethod(MethodDeclaration n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        NodeList<AnnotationExpr> declAnnotations = declarationAnnotations(n);
        NodeList<AnnotationExpr> inlineReturnAnnotations = inlineReturnTypeAnnotations(n);
        ctx.printMemberAnnotations(declAnnotations, arg);
        ctx.printModifiers(n.getModifiers());
        argumentListFormatter.printTypeParameters(n.getTypeParameters(), arg);
        if (!isNullOrEmpty(n.getTypeParameters())) {
            ctx.print(" ");
        }
        if (!inlineReturnAnnotations.isEmpty()) {
            for (Iterator<AnnotationExpr> i = inlineReturnAnnotations.iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                ctx.print(" ");
            }
        }
        ctx.accept(n.getType(), arg);
        ctx.print(" ");
        ctx.accept(n.getName(), arg);
        ctx.print("(");
        n.getReceiverParameter()
                .ifPresent(
                        rp -> {
                            ctx.accept(rp, arg);
                            if (!isNullOrEmpty(n.getParameters())) {
                                ctx.print(", ");
                            }
                        });
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && argumentListFormatter.paramsNeedWrap(n.getParameters());
        argumentListFormatter.printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            ctx.println();
        }
        ctx.print(")");
        typeClauseFormatter.printThrowsClause(n.getThrownExceptions(), arg);
        if (!n.getBody().isPresent()) {
            ctx.print(";");
        } else {
            BlockStmt body = n.getBody().get();
            boolean modernCompactEmptyMethod =
                    fmt.javaLanguageLevel().level() != JAVA8_RELEASE
                            && body.getStatements().isEmpty()
                            && body.getComment().isEmpty()
                            && body.getOrphanComments().isEmpty();
            if (modernCompactEmptyMethod) {
                ctx.print(" {}");
            } else {
                ctx.print(" ");
                ctx.accept(body, arg);
            }
        }
    }

    /** Prints a {@code record} declaration with components, optional {@code implements}, and body. */
    void formatRecord(RecordDeclaration n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        ctx.printMemberAnnotations(n.getAnnotations(), arg);
        ctx.printModifiers(n.getModifiers());
        ctx.print("record ");
        ctx.accept(n.getName(), arg);
        argumentListFormatter.printTypeParameters(n.getTypeParameters(), arg);
        ctx.print("(");
        boolean paramsWrapped = !isNullOrEmpty(n.getParameters()) && argumentListFormatter.paramsNeedWrap(n.getParameters());
        argumentListFormatter.printParametersList(n.getParameters(), arg);
        if (fmt.closingParenOnNewLine() && paramsWrapped) {
            ctx.println();
        }
        ctx.print(")");
        if (!n.getImplementedTypes().isEmpty()) {
            ctx.print(" implements ");
            for (Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
                ClassOrInterfaceType c = i.next();
                ctx.accept(c, arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
        }
        if (isNullOrEmpty(n.getMembers())) {
            ctx.print(" {}");
            return;
        }
        ctx.print(" {");
        ctx.println();
        ctx.indent();
        drainOrphanCommentsBeforeFirstBodyElement(n, n.getMembers(), arg);
        if (!isNullOrEmpty(n.getMembers())) {
            ctx.printMembers(n.getMembers(), arg);
        }
        ctx.printOrphanCommentsEnding(n);
        ctx.unindent();
        ctx.print("}");
    }

    /**
     * Prints a class or interface declaration, including type clauses, permits, compact vs full bodies,
     * and misplaced header comments.
     */
    void formatClassOrInterface(ClassOrInterfaceDeclaration n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        if (!n.isCompact()) {
            ctx.printMemberAnnotations(n.getAnnotations(), arg);
            ctx.printModifiers(n.getModifiers());
            if (n.isInterface()) {
                ctx.print("interface ");
            } else {
                ctx.print("class ");
            }
            commentUtils.pruneDuplicatedHeaderLineCommentsOnTypeClauses(n);
            List<Comment> misplacedOpeningLineComments =
                    !n.getExtendedTypes().isEmpty() || !n.getImplementedTypes().isEmpty()
                            ? commentUtils.extractAndDedupeLineCommentsOnTypeNameLine(n)
                            : commentUtils.extractLineCommentsMisplacedBeforeTypeName(n);
            ctx.accept(n.getName(), arg);
            argumentListFormatter.printTypeParameters(n.getTypeParameters(), arg);
            boolean typeClauseWrapped = false;
            if (!n.getExtendedTypes().isEmpty()) {
                typeClauseWrapped = typeClauseFormatter.printExtendsClause(n.getExtendedTypes(), arg);
            }
            if (!n.getImplementedTypes().isEmpty()) {
                typeClauseWrapped = typeClauseFormatter.printImplementsClause(n.getImplementedTypes(), arg) || typeClauseWrapped;
            }
            if (!n.getPermittedTypes().isEmpty()) {
                typeClauseWrapped =
                        typeClauseFormatter.printPermitsClause(n.getPermittedTypes(), arg) || typeClauseWrapped;
            }
            if (typeClauseWrapped && fmt.closingParenOnNewLine()) {
                ctx.println();
                ctx.print("{");
                ctx.println();
            } else {
                ctx.print(" {");
                ctx.println();
            }
            ctx.indent();
            for (Comment c : misplacedOpeningLineComments) {
                ctx.printComment(Optional.of(c), arg);
            }
            drainOrphanCommentsBeforeFirstBodyElement(n, n.getMembers(), arg);
        }
        if (!isNullOrEmpty(n.getMembers())) {
            if (n.isCompact()) {
                ctx.printCompactClassMembers(n.getMembers(), arg);
            } else {
                ctx.printMembers(n.getMembers(), arg);
            }
        }
        ctx.printOrphanCommentsEnding(n);
        if (!n.isCompact()) {
            ctx.unindent();
            ctx.print("}");
        }
    }

    /**
     * Prints an {@code enum} declaration, choosing one-line vs multiline layout and wide greedy
     * packing of constants when configured.
     */
    void formatEnum(EnumDeclaration n, Void arg) {
        ctx.printOrphanCommentsBeforeThisChildNode(n);
        ctx.printComment(n.getComment(), arg);
        ctx.printMemberAnnotations(n.getAnnotations(), arg);
        ctx.printModifiers(n.getModifiers());
        ctx.print("enum ");
        ctx.accept(n.getName(), arg);
        if (!n.getImplementedTypes().isEmpty()) {
            ctx.print(" implements ");
            for (Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
                ClassOrInterfaceType c = i.next();
                ctx.accept(c, arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
        }
        boolean hasMembers = !n.getMembers().isEmpty();
        boolean hasBodies = n.getEntries().stream().anyMatch(e -> !e.getClassBody().isEmpty());
        int flatWidth = enumConstantsFlatWidth(n.getEntries());
        int oneLineEnum = ctx.column() + ENUM_HEADER_AND_BRACE_WIDTH + flatWidth;
        boolean fitsOneLine =
                oneLineEnum <= fmt.lineLength()
                        && !hasBodies
                        && !hasMembers;
        if (fitsOneLine && n.getEntries().isNonEmpty()) {
            ctx.print(" { ");
            for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(", ");
                }
            }
            ctx.print(" }");
            return;
        }
        ctx.print(" {");
        ctx.println();
        ctx.indent();
        drainOrphanCommentsBeforeFirstBodyElement(n, n.getMembers(), n.getEntries(), arg);
        if (n.getEntries().isNonEmpty()) {
            printEnumConstants(n, arg, hasBodies);
        }
        if (hasMembers) {
            ctx.print(";");
            ctx.println();
            ctx.printMembers(n.getMembers(), arg);
        } else if (n.getEntries().isNonEmpty()) {
            ctx.println();
        }
        ctx.unindent();
        ctx.print("}");
    }

    /** Enum constant list: greedy inline (wide) or one per line. */
    private void printEnumConstants(EnumDeclaration n, Void arg, boolean hasBodies) {
        if (fmt.wrapStyle() == WrapStyle.WIDE && !hasBodies) {
            boolean first = true;
            int budget = fmt.lineLength();
            for (EnumConstantDeclaration e : n.getEntries()) {
                int need = e.toString().length() + (first ? 0 : 2);
                if (!first
                        && (ctx.column() + need > budget || argumentListFormatter.wouldExceedLineLength(need))) {
                    ctx.print(",");
                    ctx.println();
                } else if (!first) {
                    ctx.print(", ");
                }
                ctx.accept(e, arg);
                first = false;
            }
            if (fmt.trailingCommas() && !n.getEntries().isEmpty()) {
                ctx.print(",");
            }
        } else {
            for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
                ctx.accept(i.next(), arg);
                if (i.hasNext()) {
                    ctx.print(",");
                    ctx.println();
                } else if (fmt.trailingCommas()) {
                    ctx.print(",");
                }
            }
        }
    }

    /** Sum of constant text widths plus {@code ", "} separators, used for one-line enum layout. */
    private int enumConstantsFlatWidth(NodeList<EnumConstantDeclaration> entries) {
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
}
