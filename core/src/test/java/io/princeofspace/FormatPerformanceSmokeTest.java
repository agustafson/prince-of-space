package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loose throughput guard: formatting a large single class and many small passes should finish in
 * reasonable wall time on CI hardware. For rigorous benchmarking see {@code docs/benchmarks.md}.
 */
class FormatPerformanceSmokeTest {

    @Test
    void largeSingleClass_formatsWithinReasonableTime_andIdempotent() {
        StringBuilder sb = new StringBuilder(120_000);
        sb.append("package p;\nclass Big {\n");
        for (int i = 0; i < 800; i++) {
            sb.append("  void m")
                    .append(i)
                    .append("() { int x = ")
                    .append(i)
                    .append("; }\n");
        }
        sb.append("}\n");
        String src = sb.toString();

        Formatter f = new Formatter(FormatterConfig.defaults());
        long t0 = System.nanoTime();
        String once = f.format(src);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(ms).as("format ~800 methods").isLessThan(120_000L);
        assertThat(f.format(once)).isEqualTo(once);
    }

    @Test
    void repeatedSmallFormats_batchStaysFast() {
        String snippet = "class T { int x() { return 1; } }";
        Formatter f = new Formatter(FormatterConfig.defaults());
        long t0 = System.nanoTime();
        String last = snippet;
        for (int i = 0; i < 400; i++) {
            last = f.format(snippet);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(ms).as("400x small format").isLessThan(60_000L);
        String stable = Objects.requireNonNull(last);
        assertThat(f.format(stable)).isEqualTo(stable);
    }
}
