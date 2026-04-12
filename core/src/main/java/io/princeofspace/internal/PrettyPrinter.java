package io.princeofspace.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.Indentation.IndentType;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;

/**
 * Prints a {@link CompilationUnit} to source text using the formatter configuration.
 *
 * <p>Delegates to JavaParser's {@link DefaultPrettyPrinter} with a {@link PrincePrettyPrinterVisitor},
 * then post-processes through {@link BlankLineNormalizer}.
 */
final class PrettyPrinter {

    private final FormatterConfig config;

    PrettyPrinter(FormatterConfig config) {
        this.config = config;
    }

    String print(CompilationUnit cu) {
        DefaultPrinterConfiguration printerConfig = buildPrinterConfig();
        String raw = new DefaultPrettyPrinter(
                        pc -> new PrincePrettyPrinterVisitor(pc, config), printerConfig)
                .print(cu);
        return BlankLineNormalizer.normalize(raw);
    }

    private DefaultPrinterConfiguration buildPrinterConfig() {
        IndentType indentType = config.indentStyle() == IndentStyle.SPACES
                ? IndentType.SPACES
                : IndentType.TABS;
        Indentation indentation = new Indentation(indentType, config.indentSize());

        DefaultPrinterConfiguration printerConfig = new DefaultPrinterConfiguration();
        printerConfig.addOption(new DefaultConfigurationOption(ConfigOption.INDENTATION, indentation));
        printerConfig.addOption(new DefaultConfigurationOption(ConfigOption.END_OF_LINE_CHARACTER, "\n"));
        printerConfig.addOption(new DefaultConfigurationOption(ConfigOption.SPACE_AROUND_OPERATORS, true));
        return printerConfig;
    }
}
