package io.princeofspace.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRulesTest {

    @Test
    void skipsBuildSegments() {
        assertFalse(FormatterBenchmark.noSkipSegment(Path.of("src/build/Foo.java")));
    }

    @Test
    void allowsTypicalSource() {
        assertTrue(FormatterBenchmark.noSkipSegment(Path.of("spring-core/src/main/java/org/spring/Foo.java")));
    }
}
