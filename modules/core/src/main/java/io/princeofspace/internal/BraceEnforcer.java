package io.princeofspace.internal;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * Adds explicit braces to braceless control-flow bodies.
 *
 * <p>Handles {@code if}, {@code else}, {@code for}, {@code for-each}, {@code while}, and
 * {@code do-while}. Children are visited before the parent node is transformed, so nested
 * braceless statements are handled in a single pass.
 */
final class BraceEnforcer extends ModifierVisitor<Void> {

    /** Wrap a single statement in a block; copy source range when present so comment/range heuristics keep context. */
    private static BlockStmt wrapWithBlockPreservingRange(Statement stmt) {
        BlockStmt block = new BlockStmt(NodeList.nodeList(stmt));
        stmt.getRange().ifPresent(block::setRange);
        return block;
    }

    @Override
    public Visitable visit(IfStmt n, Void arg) {
        super.visit(n, arg); // depth-first: process nested if/else first

        Statement then = n.getThenStmt();
        if (!(then instanceof BlockStmt)) {
            n.setThenStmt(wrapWithBlockPreservingRange(then));
        }

        n.getElseStmt().ifPresent(elseStmt -> {
            // else-if: the IfStmt itself is never wrapped; its body will have been handled
            // by the recursive visit above. Only bare else branches need wrapping.
            if (!(elseStmt instanceof BlockStmt) && !(elseStmt instanceof IfStmt)) {
                n.setElseStmt(wrapWithBlockPreservingRange(elseStmt));
            }
        });

        return n;
    }

    @Override
    public Visitable visit(ForStmt n, Void arg) {
        super.visit(n, arg);
        Statement body = n.getBody();
        if (!(body instanceof BlockStmt)) {
            n.setBody(wrapWithBlockPreservingRange(body));
        }
        return n;
    }

    @Override
    public Visitable visit(ForEachStmt n, Void arg) {
        super.visit(n, arg);
        Statement body = n.getBody();
        if (!(body instanceof BlockStmt)) {
            n.setBody(wrapWithBlockPreservingRange(body));
        }
        return n;
    }

    @Override
    public Visitable visit(WhileStmt n, Void arg) {
        super.visit(n, arg);
        Statement body = n.getBody();
        if (!(body instanceof BlockStmt)) {
            n.setBody(wrapWithBlockPreservingRange(body));
        }
        return n;
    }

    @Override
    public Visitable visit(DoStmt n, Void arg) {
        super.visit(n, arg);
        Statement body = n.getBody();
        if (!(body instanceof BlockStmt)) {
            n.setBody(wrapWithBlockPreservingRange(body));
        }
        return n;
    }
}
