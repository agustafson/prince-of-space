package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorSuffixWidthTest {
    @Test
    void reservesTheCommentWhenWrappingTheLeftOperand() {
        String input = "class T { String f() { return \"aaaaaaa\" + \"bbbbbbb\" /* label */ + \"x\"; } }";
        for (WrapStyle wrap : WrapStyle.values()) {
            var config = FormatterConfig.builder().lineLength(40).wrapStyle(wrap).build();
            var singlePass = new Formatter(config, true);
            var formatter = new Formatter(config);
            String once = singlePass.format(input);
            assertThat(once.lines().toList()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(40));
            String output = formatter.format(input);
            assertThat(output.lines().toList()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(40));
            assertThat(output).containsOnlyOnce("/* label */").contains("\"bbbbbbb\" /* label */");
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }
}
