package io.princeofspace;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Optional aggregate timings for corpus benchmarks and profiling. Enable with
 * {@code -Dprince.bench.diagnostics=true}.
 *
 * <p>Thread-safe for parallel benchmark runs.
 */
public final class BenchDiagnostics {

    private static final String PROPERTY = "prince.bench.diagnostics";

    private static final int PASS_COUNT_SINGLE = 1;
    private static final int PASS_COUNT_TWO = 2;

    private static final double NANOSECONDS_PER_MILLISECOND = 1_000_000.0;
    private static final double NANOSECONDS_PER_MICROSECOND = 1_000.0;

    private static final LongAdder parseNanos = new LongAdder();
    private static final LongAdder transformNanos = new LongAdder();
    private static final LongAdder printNanos = new LongAdder();
    private static final LongAdder singlePassInvocations = new LongAdder();

    private static final LongAdder filesOnePass = new LongAdder();
    private static final LongAdder filesTwoPass = new LongAdder();
    private static final LongAdder filesThreePlusPass = new LongAdder();

    private static final AtomicLong filesFinished = new AtomicLong();

    private BenchDiagnostics() {}

    /** Whether phase / pass statistics are collected. */
    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));
    }

    /** Clears all counters (call once per benchmark leg). */
    public static void reset() {
        if (!isEnabled()) {
            return;
        }
        parseNanos.reset();
        transformNanos.reset();
        printNanos.reset();
        singlePassInvocations.reset();
        filesOnePass.reset();
        filesTwoPass.reset();
        filesThreePlusPass.reset();
        filesFinished.set(0);
    }

    /** Records nanoseconds for one single-pass format (parse, transform, print + blank lines). */
    public static void recordSinglePassPhases(long parseNs, long transformNs, long printNs) {
        if (!isEnabled()) {
            return;
        }
        parseNanos.add(parseNs);
        transformNanos.add(transformNs);
        printNanos.add(printNs);
        singlePassInvocations.increment();
    }

    /**
     * Records how many full format passes were required for one file (strict mode), or 1 in fast
     * mode.
     */
    public static void recordFinishedFilePassCount(int passes) {
        if (!isEnabled()) {
            return;
        }
        filesFinished.incrementAndGet();
        if (passes <= PASS_COUNT_SINGLE) {
            filesOnePass.increment();
        } else if (passes == PASS_COUNT_TWO) {
            filesTwoPass.increment();
        } else {
            filesThreePlusPass.increment();
        }
    }

    /** Markdown fragment for benchmark reports, or empty when disabled / no data. */
    public static String toMarkdownSection() {
        if (!isEnabled()) {
            return "";
        }
        long files = filesFinished.get();
        long inv = singlePassInvocations.sum();
        long p1 = filesOnePass.sum();
        long p2 = filesTwoPass.sum();
        long p3 = filesThreePlusPass.sum();
        if (files == 0 && inv == 0) {
            return "";
        }
        double avgPasses = files > 0 ? (double) inv / files : 0.0;
        String hist =
                String.format(
                        Locale.ROOT,
                        "| Passes to stable | Files |%n|----------|-------|%n"
                                + "| 1 | %d |%n"
                                + "| 2 | %d |%n"
                                + "| 3+ | %d |%n",
                        p1, p2, p3);
        String phases =
                String.format(
                        Locale.ROOT,
                        "| Phase | Total (ms) | Per single-pass invocation (μs) |%n"
                                + "|----------|-----------|------------------------------|%n"
                                + "| Parse | %.2f | %.2f |%n"
                                + "| Transform (BraceEnforcer) | %.2f | %.2f |%n"
                                + "| Print + blank lines | %.2f | %.2f |%n",
                        ms(parseNanos.sum()),
                        microsPerInvocation(parseNanos.sum(), inv),
                        ms(transformNanos.sum()),
                        microsPerInvocation(transformNanos.sum(), inv),
                        ms(printNanos.sum()),
                        microsPerInvocation(printNanos.sum(), inv));
        return """

                ### Bench diagnostics (`-Dprince.bench.diagnostics=true`)

                Aggregated over **Prince of Space (strict, fixed-point)** only.

                """
                + String.format(Locale.ROOT, "- Single-pass invocations: **%d**%n", inv)
                + String.format(Locale.ROOT, "- Files finished: **%d**%n", files)
                + String.format(Locale.ROOT, "- Average strict passes per file: **%.3f**%n", avgPasses)
                + "\n"
                + hist
                + "\n"
                + phases
                + "\n";
    }

    private static double ms(long nanos) {
        return nanos / NANOSECONDS_PER_MILLISECOND;
    }

    private static double microsPerInvocation(long totalNanos, long invocations) {
        if (invocations <= 0) {
            return 0.0;
        }
        return (totalNanos / NANOSECONDS_PER_MICROSECOND) / invocations;
    }
}
