package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import io.princeofspace.FormatResult;
import io.princeofspace.model.FormatterConfig;
import org.jspecify.annotations.Nullable;

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

    private final FormatterConfig config;
    private final int maxConvergencePasses;

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
        this.config = config;
        this.maxConvergencePasses = Math.max(0, maxConvergencePasses);
    }

    /**
     * Maximum additional passes beyond the initial format. Each extra pass re-parses the previous
     * output and formats again. The loop exits early when consecutive outputs are identical
     * (the fixed point). In practice, well-behaved inputs converge after one extra pass; the
     * remaining budget handles edge cases where JavaParser comment re-attribution needs two
     * rounds to stabilize.
     */
    private static final int DEFAULT_MAX_CONVERGENCE_PASSES = 3;
    private static final int CONFIGURED_MAX_CONVERGENCE_PASSES = resolveMaxConvergencePasses();

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
                return success;
            }
            current = next;
        }
        return new FormatResult.NonConvergent(maxConvergencePasses + 1);
    }

    private static int resolveMaxConvergencePasses() {
        @Nullable String raw = System.getProperty("prince.maxConvergencePasses");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_CONVERGENCE_PASSES;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_CONVERGENCE_PASSES;
        }
    }

    /**
     * Throws if the bundled JavaParser has no {@code LanguageLevel} for the given raw feature
     * release number (legacy 1–7 or modern 8+). Intended for tooling (for example IDE settings
     * validation) so callers do not depend on JavaParser types.
     *
     * @param release Java feature-release number as in {@link io.princeofspace.model.JavaLanguageLevel#level()}
     * @throws IllegalArgumentException if unsupported
     */
    public static void validateJavaReleaseForParser(int release) {
        JavaParserLanguageLevels.fromRelease(release);
    }

    private FormatResult singlePassFormat(String sourceCode) {
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setLanguageLevel(JavaParserLanguageLevels.toLanguageLevel(config.javaLanguageLevel()));
        ParseResult<CompilationUnit> result = new JavaParser(parserConfig).parse(sourceCode);
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
        return new FormatResult.Success(new PrettyPrinter(config).print(cu));
    }

    private void transform(CompilationUnit cu) {
        new BraceEnforcer().visit(cu, null);
        new AnnotationArranger().visit(cu, null);
    }
}
