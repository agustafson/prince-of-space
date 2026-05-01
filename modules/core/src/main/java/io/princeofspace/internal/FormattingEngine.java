package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
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
 * <p>Pipeline: parse → {@link LexicalPreservingPrinter#setup} → transform (AST visitors) → print
 * (pretty-print + blank-line normalization). Lexical preservation keeps comments and tokens
 * coherent when the AST is modified before printing.
 */
public final class FormattingEngine {

    private static final Logger LOG = System.getLogger(FormattingEngine.class.getName());

    private final int maxConvergencePasses;
    private final JavaParser parser;
    private final PrettyPrinter prettyPrinter;

    /**
     * Creates a formatting engine bound to a formatter configuration.
     *
     * @param config parser and layout options
     */
    public FormattingEngine(FormatterConfig config) {
        this(config, CONFIGURED_MAX_CONVERGENCE_PASSES);
    }

    /**
     * Visible for tests to exercise convergence-boundary behavior deterministically.
     */
    FormattingEngine(FormatterConfig config, int maxConvergencePasses) {
        this.maxConvergencePasses = Math.max(0, maxConvergencePasses);
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
     * <p>Internally applies up to a bounded number of additional format passes so that
     * the returned source is a <em>fixed point</em>: formatting it again produces identical output.
     * This guarantees idempotency ({@code format(format(x)).equals(format(x))}) even when
     * JavaParser re-attaches comments differently after the first layout pass.
     *
     * @param sourceCode Java source text to format
     * @return {@link FormatResult.Success} with formatted source, or a {@link FormatResult.Failure}
     */
    public FormatResult format(String sourceCode) {
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
        ParseResult<CompilationUnit> result = parser.parse(sourceCode);
        if (!result.isSuccessful()) {
            List<String> problems = result.getProblems().stream().map(Problem::toString).toList();
            return new FormatResult.ParseFailure(problems);
        }
        return result
            .getResult()
            .map(LexicalPreservingPrinter::setup)
            .map(this::printAfterTransform)
            .orElseGet(FormatResult.EmptyCompilationUnit::new);
    }

    private FormatResult printAfterTransform(CompilationUnit cu) {
        transform(cu);
        return new FormatResult.Success(prettyPrinter.print(cu));
    }

    private void transform(CompilationUnit cu) {
        @SuppressWarnings("ConstantConditions") // Void visitor arg is java.lang.Void; null is the only value
        Void visitorArg = null;
        new BraceEnforcer().visit(cu, visitorArg);
        new AnnotationArranger().visit(cu, visitorArg);
    }
}
