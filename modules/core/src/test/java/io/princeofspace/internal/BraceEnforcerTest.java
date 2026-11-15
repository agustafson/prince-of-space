package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BraceEnforcerTest {

    private static String applyAndPrint(String source) {
        JavaParser parser = new JavaParser(
                new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_17));
        CompilationUnit cu = parser.parse(source).getResult().orElseThrow();
        new BraceEnforcer().visit(cu, null);
        return new DefaultPrettyPrinter().print(cu);
    }

    @Test
    void wrappedBodiesRetainTheirParentsChildrenAndOwnedComments() {
        String source = """
                class T {
                    void f() {
                        if (ready) /* repeated */ first();
                        else /* repeated */ second();
                        for (;;) /* repeated */ third();
                        for (Object value : values) /* repeated */ fourth();
                        while (ready) /* repeated */ fifth();
                        do /* repeated */ sixth(); while (ready);
                    }
                }
                """;
        var unit = new JavaParser().parse(source).getResult().orElseThrow();
        var bodies = unit.findAll(ExpressionStmt.class);
        var comments = unit.getAllContainedComments();
        var ownedComments = bodies.stream().map(body -> body.getComment().orElseThrow()).toList();
        var commentOwners = ownedComments.stream().map(comment -> comment.getCommentedNode().orElseThrow()).toList();
        assertThat(bodies).hasSize(6);
        assertThat(comments).hasSize(6);

        new BraceEnforcer().visit(unit, null);

        var retainedComments = unit.getAllContainedComments();
        assertThat(retainedComments).hasSize(comments.size());
        for (int i = 0; i < comments.size(); i++) {
            assertThat(retainedComments.get(i)).isSameAs(comments.get(i));
        }
        assertThat(unit.findAll(ExpressionStmt.class)).hasSize(bodies.size());
        for (int i = 0; i < bodies.size(); i++) {
            ExpressionStmt body = bodies.get(i);
            assertThat(body.getComment()).containsSame(ownedComments.get(i));
            assertThat(ownedComments.get(i).getCommentedNode()).containsSame(commentOwners.get(i));
            BlockStmt block = (BlockStmt) body.getParentNode().orElseThrow();
            assertThat(block.getStatement(0)).isSameAs(body);
            assertThat(block.getChildNodes()).hasSize(1);
            assertThat(block.getChildNodes().get(0)).isSameAs(body);
            assertThat(block.getRange()).isEqualTo(body.getRange());
            assertThat(unit.findAll(ExpressionStmt.class)).anySatisfy(found -> assertThat(found).isSameAs(body));
        }
    }

    // ── if / else ────────────────────────────────────────────────────────────

    @Test
    void if_withoutBraces_bracesAdded() {
        String output = applyAndPrint(
                "class T { void m() { if (x) return 1; } }");
        assertThat(output).contains("if (x) {").contains("return 1;");
    }

    @Test
    void if_alreadyBraced_unchanged() {
        String output = applyAndPrint(
                "class T { void m() { if (x) { return 1; } } }");
        // just one opening brace for the if
        assertThat(output.chars().filter(c -> c == '{').count()).isGreaterThanOrEqualTo(3);
        assertThat(output).contains("if (x) {");
    }

    @Test
    void ifElse_bothWithoutBraces_bothBraced() {
        String output = applyAndPrint(
                "class T { void m() { if (x) return 1; else return 2; } }");
        assertThat(output).contains("if (x) {").contains("} else {");
    }

    @Test
    void elseIf_chainedBraceless_allBodiesBraced() {
        String source = """
                class T {
                    String m(int v) {
                        if (v < 0) return "neg";
                        else if (v == 0) return "zero";
                        else return "pos";
                    }
                }
                """;
        String output = applyAndPrint(source);
        assertThat(output)
                .contains("if (v < 0) {")
                .contains("} else if (v == 0) {")
                .contains("} else {");
    }

    @Test
    void nestedBracelessIf_allBraced() {
        String output = applyAndPrint(
                "class T { void m() { if (a) if (b) x(); } }");
        // method body + outer if body + inner if body = at least 3
        assertThat(output.chars().filter(c -> c == '{').count()).isGreaterThanOrEqualTo(3);
    }

    // ── for ──────────────────────────────────────────────────────────────────

    @Test
    void forLoop_withoutBraces_bracesAdded() {
        String output = applyAndPrint(
                "class T { void m() { for (int i = 0; i < 10; i++) x(); } }");
        assertThat(output).matches("(?s).*for \\(.*\\) \\{.*x\\(\\);.*\\}.*");
    }

    @Test
    void forEach_withoutBraces_bracesAdded() {
        String output = applyAndPrint(
                "class T { void m(java.util.List<String> items) { for (String s : items) System.out.println(s); } }");
        assertThat(output).contains("for (String s : items) {");
    }

    // ── while / do ───────────────────────────────────────────────────────────

    @Test
    void whileLoop_withoutBraces_bracesAdded() {
        String output = applyAndPrint(
                "class T { void m() { while (running) tick(); } }");
        assertThat(output).contains("while (running) {").contains("tick();");
    }

    @Test
    void doWhile_withoutBraces_bracesAdded() {
        String output = applyAndPrint(
                "class T { void m() { do tick(); while (running); } }");
        assertThat(output).contains("do {").contains("tick();");
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void braceEnforcer_isIdempotent() {
        String source = """
                class T {
                    void m(int v) {
                        if (v < 0) return;
                        for (int i = 0; i < v; i++) x();
                        while (running) tick();
                    }
                }
                """;
        String once = applyAndPrint(source);
        String twice = applyAndPrint(once);
        assertThat(twice).isEqualTo(once);
    }
}
