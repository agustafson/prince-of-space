package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import io.princeofspace.BenchDiagnostics;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

/**
 * JavaParser-aware implementation of the format pipeline.
 *
 * <p>This is the only class in {@code io.princeofspace.internal} that is {@code public}; it must
 * be visible to {@link io.princeofspace.Formatter}. All other classes in this package are
 * package-private implementation details.
 *
 * <p>Pipeline: parse → transform (AST visitors) → print (pretty-print + blank-line normalization).
 *
 * <p>Note: this engine intentionally does <em>not</em> call
 * {@code LexicalPreservingPrinter.setup} — the printer is a custom
 * {@code DefaultPrettyPrinterVisitor}-based subclass ({@link PrincePrettyPrinterVisitor}) that
 * does not consult LPP node text. Skipping setup avoids attaching the LPP observer that would
 * otherwise process every AST mutation made by the visitor (in particular orphan comment removals
 * during printing) and pay the per-pass cost of building per-node {@code NodeText} state. This is
 * safe because convergence still re-parses from output text on each iteration, so any mutations
 * applied to the compilation unit during a single pass are discarded after printing.
 */
public final class FormattingEngine {

    private static final Logger LOG = System.getLogger(FormattingEngine.class.getName());

    private final int maxConvergencePasses;
    private final boolean fastSinglePass;
    private final JavaParser parser;
    private final PrettyPrinter prettyPrinter;

    /**
     * Creates a formatting engine bound to a formatter configuration (strict convergence toward a
     * fixed point).
     *
     * @param config parser and layout options
     */
    public FormattingEngine(FormatterConfig config) {
        this(config, CONFIGURED_MAX_CONVERGENCE_PASSES, false);
    }

    /**
     * Creates an engine with optional fast single-pass formatting (no convergence loop). Intended for
     * throughput benchmarking versus single-invocation JVM formatters — not the default product path.
     *
     * @param config parser and layout options
     * @param fastSinglePass when {@code true}, {@link #format(String)} returns after one successful
     *     {@link #singlePassFormat(String)}
     */
    public FormattingEngine(FormatterConfig config, boolean fastSinglePass) {
        this(config, CONFIGURED_MAX_CONVERGENCE_PASSES, fastSinglePass);
    }

    /**
     * Visible for tests to exercise convergence-boundary behavior deterministically.
     */
    FormattingEngine(FormatterConfig config, int maxConvergencePasses) {
        this(config, maxConvergencePasses, false);
    }

    FormattingEngine(FormatterConfig config, int maxConvergencePasses, boolean fastSinglePass) {
        this.maxConvergencePasses = Math.max(0, maxConvergencePasses);
        this.fastSinglePass = fastSinglePass;
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setLanguageLevel(JavaParserLanguageLevels.toLanguageLevel(config.javaLanguageLevel()));
        this.parser = new JavaParser(parserConfig);
        this.prettyPrinter = new PrettyPrinter(config);
    }

    /**
     * Maximum additional passes beyond the initial format. Each extra pass re-parses the previous
     * output and formats again. The loop exits early when consecutive outputs are identical
     * (the fixed point). In practice, most inputs converge quickly; a larger budget covers WIDE mode
     * at a short line length, where greedy wraps can cascade until stable (still monotonic toward a
     * fixed point—JavaParser comment re-attribution remains a secondary driver).
     */
    private static final int DEFAULT_MAX_CONVERGENCE_PASSES = 11;
    private static final int CONVERGENCE_LOG_THRESHOLD = 1;
    private static final int CONFIGURED_MAX_CONVERGENCE_PASSES =
            interpretMaxConvergencePasses(System.getProperty("prince.maxConvergencePasses"));

    /**
     * Parses and formats the given source, or returns a typed failure without throwing.
     *
     * <p>When {@link #fastSinglePass} is {@code false}, internally applies up to a bounded number of
     * additional format passes so that the returned source is a <em>fixed point</em>: formatting it
     * again produces identical output. This guarantees idempotency (
     * {@code format(format(x)).equals(format(x))}) even when JavaParser re-attaches comments
     * differently after the first layout pass.
     *
     * <p>When {@link #fastSinglePass} is {@code true}, returns after one successful parse/transform/print
     * pass (benchmark throughput mode).
     *
     * @param sourceCode Java source text to format
     * @return {@link FormatResult.Success} with formatted source, or a {@link FormatResult.Failure}
     */
    public FormatResult format(String sourceCode) {
        if (fastSinglePass) {
            FormatResult result = singlePassFormat(sourceCode);
            if (BenchDiagnostics.isEnabled() && result instanceof FormatResult.Success) {
                BenchDiagnostics.recordFinishedFilePassCount(1);
            }
            return result;
        }

        String current = sourceCode;
        for (int pass = 0; pass <= maxConvergencePasses; pass++) {
            FormatResult result = singlePassFormat(current);
            if (!(result instanceof FormatResult.Success success)) {
                return result;
            }
            String next = success.formattedSource();
            if (next.equals(current)) {
                if (pass > CONVERGENCE_LOG_THRESHOLD) {
                    LOG.log(Level.DEBUG,
                            "Convergence required {0} passes (budget {1})",
                            pass + 1, maxConvergencePasses + 1);
                }
                if (BenchDiagnostics.isEnabled()) {
                    BenchDiagnostics.recordFinishedFilePassCount(pass + 1);
                }
                return success;
            }
            current = next;
        }
        LOG.log(Level.WARNING,
                "Formatting did not converge within {0} passes", maxConvergencePasses + 1);
        return new FormatResult.NonConvergent(maxConvergencePasses + 1);
    }

    /**
     * Interprets the raw {@code prince.maxConvergencePasses} property text. Visible for tests.
     *
     * <p>Non-integer values and explicit negatives are rejected: the former falls back to the
     * default pass budget; the latter clamps to zero. Both cases log at {@link Level#WARNING} because
     * the user set the property explicitly.
     */
    static int interpretMaxConvergencePasses(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_CONVERGENCE_PASSES;
        }
        String stripped = raw.strip();
        try {
            int parsed = Integer.parseInt(stripped);
            if (parsed < 0) {
                LOG.log(Level.WARNING,
                        "Ignoring invalid prince.maxConvergencePasses={0} (negative); using 0",
                        raw);
                return 0;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            LOG.log(Level.WARNING,
                    "Ignoring invalid prince.maxConvergencePasses={0} (not an integer); using default {1}",
                    raw, DEFAULT_MAX_CONVERGENCE_PASSES);
            return DEFAULT_MAX_CONVERGENCE_PASSES;
        }
    }

    /**
     * Throws if the bundled JavaParser has no {@code LanguageLevel} for the given raw feature
     * release number (8+). Intended for tooling (for example IDE settings validation) so callers
     * do not depend on JavaParser types.
     *
     * @param release Java feature-release number as in {@link io.princeofspace.model.JavaLanguageLevel#level()}
     * @throws IllegalArgumentException if unsupported (release {@code < 8} or unknown to bundled JavaParser)
     */
    public static void validateJavaReleaseForParser(int release) {
        JavaParserLanguageLevels.fromRelease(release);
    }

    private FormatResult singlePassFormat(String sourceCode) {
        long t0 = System.nanoTime();
        ParseResult<CompilationUnit> result = parser.parse(sourceCode);
        long tAfterParse = System.nanoTime();
        if (!result.isSuccessful()) {
            if (BenchDiagnostics.isEnabled()) {
                BenchDiagnostics.recordSinglePassPhases(tAfterParse - t0, 0, 0);
            }
            List<String> problems = result.getProblems().stream().map(Problem::toString).toList();
            return new FormatResult.ParseFailure(problems);
        }
        return result.getResult()
                .map(cu -> printAfterTransform(cu, t0, tAfterParse))
                .orElseGet(
                        () -> {
                            if (BenchDiagnostics.isEnabled()) {
                                BenchDiagnostics.recordSinglePassPhases(tAfterParse - t0, 0, 0);
                            }
                            return new FormatResult.EmptyCompilationUnit();
                        });
    }

    private FormatResult printAfterTransform(CompilationUnit cu, long t0, long tAfterParse) {
        long tBeforeTransform = System.nanoTime();
        transform(cu);
        long tAfterTransform = System.nanoTime();
        String printed = prettyPrinter.print(cu);
        long tAfterPrint = System.nanoTime();
        if (BenchDiagnostics.isEnabled()) {
            BenchDiagnostics.recordSinglePassPhases(
                    tAfterParse - t0,
                    tAfterTransform - tBeforeTransform,
                    tAfterPrint - tAfterTransform);
        }
        return new FormatResult.Success(printed);
    }

    /**
     * Normalizes the AST before pretty-printing. {@link BraceEnforcer} adds block bodies to braceless
     * control flow.
     *
     * <p>During {@link PrettyPrinter#print}, the visitor may {@code remove()} comment nodes as they are
     * printed (so they are not emitted twice). The compilation unit passed in is local to this single
     * pass — the convergence loop re-parses from output text on each iteration, so any mutation here
     * is discarded after {@link #printAfterTransform} returns and never observed by callers.
     */
    private void transform(CompilationUnit cu) {
        @SuppressWarnings("ConstantConditions") // Void visitor arg is java.lang.Void; null is the only value
        Void visitorArg = null;
        new BraceEnforcer().visit(cu, visitorArg);
    }
}
