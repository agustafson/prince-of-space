package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AnnotationArranger} does not corrupt the AST.
 * The arranger is currently a no-op placeholder; these tests guard against
 * regressions when real transformation logic is added.
 */
class AnnotationArrangerTest {

    private static String applyAndPrint(String source) {
        JavaParser parser = new JavaParser(
                new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_17));
        CompilationUnit cu = parser.parse(source).getResult().orElseThrow();
        new AnnotationArranger().visit(cu, null);
        return new DefaultPrettyPrinter().print(cu);
    }

    @Test
    void declarationAnnotation_preserved() {
        String src = "class T { @Override public String toString() { return \"\"; } }";
        String out = applyAndPrint(src);
        assertThat(out).contains("@Override");
    }

    @Test
    void multipleAnnotations_allPreserved() {
        String src = """
                class T {
                    @Deprecated
                    @SuppressWarnings("all")
                    void m() {}
                }
                """;
        String out = applyAndPrint(src);
        assertThat(out).contains("@Deprecated").contains("@SuppressWarnings");
    }

    @Test
    void parameterAnnotation_preserved() {
        String src = "class T { void m(@SuppressWarnings(\"all\") String s) {} }";
        String out = applyAndPrint(src);
        assertThat(out).contains("@SuppressWarnings");
    }

    @Test
    void classWithNoAnnotations_unchanged() {
        String src = "class T { int x; void m() {} }";
        String out = applyAndPrint(src);
        assertThat(out).doesNotContain("@");
    }

    @Test
    void arranger_isIdempotent() {
        String src = """
                class T {
                    @Override
                    public String toString() { return ""; }
                }
                """;
        String once = applyAndPrint(src);
        String twice = applyAndPrint(once);
        assertThat(twice).isEqualTo(once);
    }
}
