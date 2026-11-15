package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorCommentEscapingTest {
    private static final List<String> INPUTS = List.of(
            "class T { boolean f(int n, boolean ok) { return n != 0 /* not MARKER */ && ok; } }",
            "class T { Object f(boolean ok) { return ok // not MARKER\n ? first() : second(); } }",
            "class T { Object f(boolean ok) { return ok ? /* not MARKER */ first() : second(); } }",
            "class T { Object f(boolean ok) { return ok ? first() /* not MARKER */ : second(); } }",
            "class T { Object f(boolean ok) { return ok ? first() : /* not MARKER */ second(); } }",
            "class T { void f(boolean ok) { if (ok) // not MARKER\n { accept(); } } }",
            "class T { void f() { foo(). // not MARKER\n bar(); } }",
            "class T { void f() { foo().bar // not MARKER\n (); } }");

    private record Marker(String input, String output) {}

    @Test
    void escapesCommentContentOnceBeforePrintingEveryGap() {
        List<Marker> markers = List.of(
                new Marker("\\u0000", "\\u0000"),
                new Marker("\\uD800", "\\uD800"),
                new Marker("\\u005Cu0000", "\\u005Cu0000"),
                new Marker("\0", "\\u0000"),
                new Marker(String.valueOf((char) 0xD800), "\\uD800"));
        for (WrapStyle wrap : WrapStyle.values()) {
            var config = FormatterConfig.builder().lineLength(80).wrapStyle(wrap).build();
            var singlePass = new Formatter(config, true);
            var formatter = new Formatter(config);
            for (Marker marker : markers) {
                for (String template : INPUTS) {
                    String input = template.replace("MARKER", marker.input());
                    String once = singlePass.format(input);
                    assertThat(once).containsOnlyOnce("not " + marker.output());
                    String output = formatter.format(input);
                    assertThat(output).containsOnlyOnce("not " + marker.output());
                    assertThat(formatter.format(output)).isEqualTo(output);
                    assertThat(output).doesNotContain("\0", String.valueOf((char) 0xD800));
                }
            }
        }
    }
}
