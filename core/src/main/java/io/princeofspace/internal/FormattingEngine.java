package io.princeofspace.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import io.princeofspace.FormatterException;
import io.princeofspace.model.FormatterConfig;

import java.util.stream.Collectors;

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

    public FormattingEngine(FormatterConfig config) {
        this.config = config;
    }

    public String format(String sourceCode) {
        CompilationUnit cu = parse(sourceCode);
        transform(cu);
        return print(cu);
    }

    private CompilationUnit parse(String sourceCode) {
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setLanguageLevel(config.javaLanguageLevel());
        ParseResult<CompilationUnit> result = new JavaParser(parserConfig).parse(sourceCode);
        if (!result.isSuccessful()) {
            String problems = result.getProblems().stream()
                    .map(Problem::toString)
                    .collect(Collectors.joining("\n"));
            throw new FormatterException("Parse failed:\n" + problems);
        }
        CompilationUnit cu =
                result.getResult().orElseThrow(() -> new FormatterException("Parser returned no result"));
        return LexicalPreservingPrinter.setup(cu);
    }

    private void transform(CompilationUnit cu) {
        new BraceEnforcer().visit(cu, null);
        new AnnotationArranger().visit(cu, null);
    }

    private String print(CompilationUnit cu) {
        return new PrettyPrinter(config).print(cu);
    }
}
