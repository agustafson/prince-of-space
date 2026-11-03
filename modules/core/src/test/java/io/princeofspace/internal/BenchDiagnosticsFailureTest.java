package io.princeofspace.internal;

import io.princeofspace.BenchDiagnostics;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated("Changes aggregate benchmark diagnostics")
class BenchDiagnosticsFailureTest {

    @ParameterizedTest
    @MethodSource("failedSources")
    void countsFailedPassesWithoutFinishingAFile(String source) {
        String property = "prince.bench.diagnostics";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "true");
            BenchDiagnostics.reset();
            FormatResult result = new FormattingEngine(FormatterConfig.defaults(), true).format(source);
            assertThat(result).isInstanceOf(FormatResult.ParseFailure.class);
            assertThat(BenchDiagnostics.toMarkdownSection()).contains(
                    "Single-pass invocations: **1**", "Files finished: **0**");
        } finally {
            BenchDiagnostics.reset();
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    static Stream<String> failedSources() {
        return Stream.of("class {", "class T {} // \\" + "uZZZZ",
                "class T {} // " + "\\u005C" + "\\\\" + "u000a");
    }
}
