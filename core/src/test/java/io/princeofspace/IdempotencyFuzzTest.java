package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style idempotency: {@code format(format(x)) == format(x)} across varied configs and inputs.
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

    @Test
    void formatTwiceStable_forPseudoRandomConfigs() {
        SplittableRandom rng = new SplittableRandom(0xFEEDBEEFL);
        for (int round = 0; round < 80; round++) {
            int preferred = 40 + rng.nextInt(90);
            int max = preferred + rng.nextInt(100);
            WrapStyle[] styles = {WrapStyle.WIDE, WrapStyle.BALANCED, WrapStyle.NARROW};
            FormatterConfig cfg =
                    FormatterConfig.builder()
                            .preferredLineLength(preferred)
                            .maxLineLength(max)
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
}
