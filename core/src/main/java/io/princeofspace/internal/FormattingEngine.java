package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import io.princeofspace.FormatResult;
import io.princeofspace.model.FormatterConfig;

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

    /**
     * @param config parser and layout options
     */
    public FormattingEngine(FormatterConfig config) {
        this.config = config;
    }

    /**
     * Parses and formats the given source, or returns a typed failure without throwing.
     *
     * @param sourceCode Java source text to format
     * @return {@link FormatResult.Success} with formatted source, or a {@link FormatResult.Failure}
     */
    public FormatResult format(String sourceCode) {
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setLanguageLevel(config.javaLanguageLevel());
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
