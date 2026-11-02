package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayCommentDuplicationReproducerTest {

    @Test
    void trailingArrayCommentsArePreservedOnce() {
        String input = """
                class ArrayComments {
                  private static final byte[] RANGES = {
                    (byte) 0x0A, 0, 0, 0, /**/ (byte) 0xFF, 0, 0, 0, // 10.0.0.0/8
                    (byte) 0xAC, (byte) 0x10, 0, 0, /**/ (byte) 0xFF, (byte) 0xF0, 0, 0, // 172.16.0.0/12
                  };
                }
                """;
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .indentSize(2)
                .lineLength(100)
                .javaLanguageLevel(JavaLanguageLevel.of(25))
                .build());

        String output = formatter.format(input);

        assertThat(formatter.format(output)).isEqualTo(output);
        assertThat(output).containsOnlyOnce("// 10.0.0.0/8").containsOnlyOnce("// 172.16.0.0/12");
        assertThat(output).contains("0, // 10.0.0.0/8", "0 // 172.16.0.0/12");
        assertThat(output.indexOf("// 10.0.0.0/8")).isLessThan(output.indexOf("(byte) 0xAC"));
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void preservesBlockCommentBeforeTrailingLineComment(WrapStyle wrapStyle) {
        String input = "class A { int[] values = {1, 2, /* first */ /* middle */ // second\n3, 4}; }";
        for (boolean trailingCommas : new boolean[] {false, true}) {
            Formatter formatter = new Formatter(FormatterConfig.builder()
                    .wrapStyle(wrapStyle)
                    .trailingCommas(trailingCommas)
                    .build());

            String output = formatter.format(input);

            assertThat(output).containsOnlyOnce("/* first */").containsOnlyOnce("/* middle */")
                    .containsOnlyOnce("// second");
            assertThat(output).contains("2, /* first */ /* middle */ // second");
            assertThat(output.indexOf("// second")).isLessThan(output.indexOf("3,"));
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/* first */", "/* first\n continued */"})
    void preservesMultilineSeparatorBeforeTrailingComment(String separator) {
        String input = "class A { int[] values = {1, 2, " + separator + " // end\n3, 4}; }";
        Formatter formatter = new Formatter(FormatterConfig.builder().build());

        String output = formatter.format(input);

        assertThat(output).containsOnlyOnce("first").containsOnlyOnce("// end");
        assertThat(output.indexOf("first")).isLessThan(output.indexOf("// end"));
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void preservesDistinctIdenticalCommentsAndNestedRows(WrapStyle wrapStyle) {
        String input = """
                class A {
                  int[][] values = {
                    { // leading
                      1, 2, // same
                      3, 4 // same
                    },
                    {5, 6 // last
                    }
                  };
                }
                """;
        for (boolean trailingCommas : new boolean[] {false, true}) {
            Formatter formatter = new Formatter(FormatterConfig.builder()
                    .wrapStyle(wrapStyle)
                    .trailingCommas(trailingCommas)
                    .build());

            String output = formatter.format(input);

            assertThat(output).containsOnlyOnce("// leading").containsOnlyOnce("// last");
            assertThat(output.split("// same", -1)).hasSize(3);
            assertThat(output).contains("2, // same", "4" + (trailingCommas ? "," : "") + " // same");
            assertThat(output).contains("6" + (trailingCommas ? "," : "") + " // last");
            assertThat(output.indexOf("// leading")).isLessThan(output.indexOf("1,"));
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }
}
