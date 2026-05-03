package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for {@link Formatter#Formatter(FormatterConfig, boolean)} benchmark mode. */
class FormatterFastSinglePassTest {

    @Test
    void fastSinglePass_formatsWithoutThrowing() {
        String src =
                """
                class T {
                    void m() {
                        if (x)
                            return;
                    }
                }
                """;
        Formatter fast = new Formatter(FormatterConfig.defaults(), true);
        assertThat(fast.format(src)).contains("class T");
    }

    @Test
    void fastSinglePass_onAlreadyStable_matchesStrict() {
        String messy = "class T { void m(){while(true){}} }";
        Formatter strict = new Formatter(FormatterConfig.defaults());
        String stable = strict.format(messy);
        Formatter fast = new Formatter(FormatterConfig.defaults(), true);
        assertThat(fast.format(stable)).isEqualTo(stable);
    }
}
