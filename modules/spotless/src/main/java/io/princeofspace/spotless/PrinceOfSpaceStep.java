package io.princeofspace.spotless;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;

/**
 * Spotless {@link FormatterStep} that delegates to {@link Formatter}. The step is serializable so Gradle
 * and Maven can isolate it in a separate classloader.
 */
public final class PrinceOfSpaceStep {

    private static final String NAME = "prince-of-space-java";

    private PrinceOfSpaceStep() {
    }

    /**
     * Creates {@link FormatterStep} based on the provided {@link FormatterConfig}.
     *
     * <p>Spotless requires the state object passed to {@link FormatterStep#create} to be
     * {@link java.io.Serializable} so the step can be isolated in a separate classloader and still
     * round-trip through Gradle’s configuration cache. {@link FormatterConfig} implements
     * {@code Serializable} for that contract; do not pass a non-serializable config wrapper.
     *
     * @param config formatter options (must be {@link java.io.Serializable})
     * @return {@link FormatterStep} based on the provided {@link FormatterConfig}
     */
    public static FormatterStep create(FormatterConfig config) {
        return FormatterStep.create(NAME, config, PrinceOfSpaceStep::formatterFunc);
    }

    private static FormatterFunc formatterFunc(FormatterConfig config) {
        Formatter formatter = new Formatter(config);
        return FormatterFunc.needsFile((unix, file) -> formatter.format(unix, file.toPath()));
    }
}
