package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssertStmtFormattingTest {

    @Test
    void assertWithoutMessage_hasNoTrailingSpace() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out = f.format("class T { void m(boolean a) { assert a; } }");
        String line = lineContainingAssert(out);
        assertThat(line).endsWith("assert a;").as("line: %s", line);
        assertThat(line).doesNotContain(" :");
        assertThat(f.format(out)).isEqualTo(out);
    }

    @Test
    void assertWithMessage_hasSingleSpaceAfterColon() {
        Formatter f = new Formatter(FormatterConfig.builder().lineLength(120).build());
        String out = f.format("class T { void m(boolean a) { assert a : \"m\"; } }");
        String line = lineContainingAssert(out);
        int colon = line.indexOf(':');
        assertThat(colon).isGreaterThan(0);
        assertThat(line.charAt(colon + 1))
                .as("exactly one space between ':' and the message, line=%s", line)
                .isEqualTo(' ');
        assertThat(line).doesNotContain(" :  ");
        assertThat(f.format(out)).isEqualTo(out);
    }

    private static String lineContainingAssert(String out) {
        for (String line : out.split("\\R", -1)) {
            if (line.contains("assert ")) {
                return line;
            }
        }
        throw new AssertionError("no assert in:\n" + out);
    }
}
