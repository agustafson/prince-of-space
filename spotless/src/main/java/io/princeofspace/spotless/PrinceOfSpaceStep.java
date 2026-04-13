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

    public static final String NAME = "prince-of-space-java";

    private PrinceOfSpaceStep() {}

    /**
     * @param config formatter options (must be {@link java.io.Serializable})
     */
    public static FormatterStep create(FormatterConfig config) {
        return FormatterStep.create(NAME, config, PrinceOfSpaceStep::formatterFunc);
    }

    private static FormatterFunc formatterFunc(FormatterConfig config) {
        return FormatterFunc.needsFile(
                (unix, file) -> new Formatter(config).format(unix, file.toPath()));
    }
}
