package io.princeofspace.internal;

import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for {@link WrapStyle#WIDE} at {@code lineLength = 80}: Spring Framework files
 * that previously exceeded the engine's convergence pass budget under
 * {@link io.princeofspace.RealWorldEvalTest} (WIDE at 80: output can require many monotonic passes
 * before it is a fixed point).
 */
class WideSpringCorpusConvergenceRegressionTest {

    private static final FormatterConfig W80_WIDE =
            FormatterConfig.builder().lineLength(80).wrapStyle(WrapStyle.WIDE).build();

    private static String resourceUtf8(String name) throws IOException {
        String path = "/io/princeofspace/internal/convergence/" + name;
        try (InputStream in = WideSpringCorpusConvergenceRegressionTest.class.getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertWide80Converges(String label, String source) {
        FormattingEngine engine = new FormattingEngine(W80_WIDE);
        FormatResult result = engine.format(source);
        assertThat(result).as(label).isInstanceOf(FormatResult.Success.class);
        FormatResult.Success ok = (FormatResult.Success) result;
        FormatResult second = engine.format(ok.formattedSource());
        assertThat(second).as(label + " second pass").isInstanceOf(FormatResult.Success.class);
        assertThat(((FormatResult.Success) second).formattedSource()).as(label).isEqualTo(ok.formattedSource());
    }

    @Test
    void routerFunctionsTests_converges() throws IOException {
        assertWide80Converges("RouterFunctionsTests", resourceUtf8("RouterFunctionsTests.java"));
    }

    @Test
    void yamlPropertiesFactoryBeanTests_converges() throws IOException {
        assertWide80Converges("YamlPropertiesFactoryBeanTests", resourceUtf8("YamlPropertiesFactoryBeanTests.java"));
    }

    @Test
    void restClientIntegrationTests_converges() throws IOException {
        assertWide80Converges("RestClientIntegrationTests", resourceUtf8("RestClientIntegrationTests.java"));
    }
}
