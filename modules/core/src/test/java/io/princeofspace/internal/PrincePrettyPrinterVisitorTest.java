package io.princeofspace.internal;

import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrincePrettyPrinterVisitorTest {

    @Test
    void defaultVisit_rejectsUnsupportedNode() {
        DefaultPrinterConfiguration printerConfig = new DefaultPrinterConfiguration();
        printerConfig.addOption(new DefaultConfigurationOption(ConfigOption.END_OF_LINE_CHARACTER, "\n"));
        PrincePrettyPrinterVisitor visitor =
                new PrincePrettyPrinterVisitor(printerConfig, FormatterConfig.defaults(), "\n");
        assertThatThrownBy(() -> visitor.defaultVisit(new StringLiteralExpr("a"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BinaryExpr");
    }
}
