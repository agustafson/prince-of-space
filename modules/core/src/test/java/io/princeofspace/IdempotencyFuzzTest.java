package io.princeofspace;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style idempotency: {@code format(format(x)) == format(x)} across varied configs and inputs.
 *
 * <p>Iterations default to 200; override with {@code -Dio.princeofspace.fuzzIterations=N}.
 */
class IdempotencyFuzzTest {

    private static final String[] SNIPPETS = {
        "class A {}",
        "interface I { String m(); }",
        "enum E { A, B }",
        "record R(int x) {}",
        """
        class T {
            void m() {
                if (true) return;
            }
        }
        """,
        """
        class T {
            String s() {
                return "a" + "b" + "c";
            }
        }
        """
    };

    private static int fuzzIterations() {
        String prop = System.getProperty("io.princeofspace.fuzzIterations");
        if (prop != null && !prop.isEmpty()) {
            return Integer.parseInt(prop);
        }
        return 200;
    }

    @Test
    void formatTwiceStable_forPseudoRandomConfigs() {
        SplittableRandom rng = new SplittableRandom(0xFEEDBEEFL);
        for (int round = 0; round < fuzzIterations(); round++) {
            int lineLen = 40 + rng.nextInt(90);
            WrapStyle[] styles = {WrapStyle.WIDE, WrapStyle.BALANCED, WrapStyle.NARROW};
            FormatterConfig cfg =
                    FormatterConfig.builder()
                            .lineLength(lineLen)
                            .continuationIndentSize(4 + rng.nextInt(2) * 4)
                            .wrapStyle(styles[rng.nextInt(styles.length)])
                            .closingParenOnNewLine(rng.nextBoolean())
                            .trailingCommas(rng.nextBoolean())
                            .build();
            Formatter f = new Formatter(cfg);
            String input = SNIPPETS[rng.nextInt(SNIPPETS.length)];
            String once = f.format(input);
            assertThat(f.format(once)).as("round %s config %s", round, cfg).isEqualTo(once);
        }
    }

    @Test
    void formatTwiceStable_forAstBuiltCompilationUnit() {
        CompilationUnit cu = new CompilationUnit("generated.fuzz");
        ClassOrInterfaceDeclaration cls = cu.addClass("AstGen");
        cls.addMethod("run", Modifier.Keyword.PUBLIC)
                .setType("void")
                .getBody()
                .get()
                .addStatement("System.out.println(1);");
        String input = cu.toString();
        Formatter f = new Formatter(FormatterConfig.defaults());
        String once = f.format(input);
        assertThat(f.format(once)).isEqualTo(once);
    }
}
