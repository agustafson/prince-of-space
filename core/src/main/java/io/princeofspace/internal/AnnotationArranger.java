package io.princeofspace.internal;

import com.github.javaparser.ast.visitor.ModifierVisitor;

/**
 * Normalizes annotation placement at the AST level.
 *
 * <p>In Phase 2, {@code DefaultPrettyPrinterVisitor.printMemberAnnotations} already prints each
 * declaration annotation on its own line, so no AST transformation is required for the common
 * cases.
 *
 * <p>This visitor is a placeholder for future phases that will handle:
 *
 * <ul>
 *   <li>Separating mis-categorised declaration annotations from type-use annotations
 *   <li>Ordering annotations canonically
 * </ul>
 */
final class AnnotationArranger extends ModifierVisitor<Void> {}
