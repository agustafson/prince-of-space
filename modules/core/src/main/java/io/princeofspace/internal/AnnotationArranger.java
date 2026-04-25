package io.princeofspace.internal;

import com.github.javaparser.ast.visitor.ModifierVisitor;

/**
 * Optional AST pass for annotation placement, run after {@link BraceEnforcer} in {@link
 * io.princeofspace.internal.FormattingEngine}.
 *
 * <p>Declaration layout (one declaration annotation per line, type-use annotations adjacent to
 * types) is primarily enforced by the custom pretty printer. This visitor remains a
 * {@link ModifierVisitor} hook for future AST-level fixes (e.g. moving a mis-attached
 * type-use marker). End-to-end expectations are covered by {@code AnnotationArrangerTest} using
 * {@link io.princeofspace.Formatter}.
 */
final class AnnotationArranger extends ModifierVisitor<Void> {}
