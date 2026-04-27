package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.TooManyMethods"})
class CanonicalRulesPropertyTest {

    // Rule 1: Idempotency is mandatory
    @Property(tries = 80)
    void rule1_idempotency_holds_for_generated_binary_and_call_shapes(
            @ForAll("identifier") String identifier,
            @ForAll("argumentCount") int argumentCount) {
        String source = """
                class C {
                    Object m(boolean a, boolean b, boolean c, String x, String y, String z) {
                        return veryLongCallNameForRule1_%s(%s) && a && b && c
                                ? anotherVeryLongCallNameForRule1_%s(x, y, z)
                                : fallbackValueForRule1_%s(x, y, z);
                    }
                }
                """.formatted(identifier, longArgs(argumentCount, identifier), identifier, identifier);

        Formatter formatter = formatter(80, WrapStyle.BALANCED, true, 4);
        String once = formatter.format(source);
        String twice = formatter.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Property(tries = 80)
    void rule1_idempotency_holds_for_generated_nested_ternary_shapes(
            @ForAll("identifier") String identifier,
            @ForAll("closingParenOnNewLine") boolean closingParenOnNewLine) {
        String source = """
                class C {
                    Object m() {
                        return rootRule1_%s(longConditionExpression_%s
                                ? innerRule1_%s(longArgumentOne_%s, longArgumentTwo_%s, longArgumentThree_%s, longArgumentFour_%s, longArgumentFive_%s)
                                : otherRule1_%s(longArgumentOne_%s, longArgumentTwo_%s, longArgumentThree_%s, longArgumentFour_%s, longArgumentFive_%s));
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier, identifier);

        Formatter formatter = formatter(80, WrapStyle.BALANCED, closingParenOnNewLine, 4);
        String once = formatter.format(source);
        String twice = formatter.format(once);
        assertThat(twice).isEqualTo(once);
    }

    // Rule 2: Braces and brace placement
    @Property(tries = 60)
    void rule2_braceless_control_flow_is_always_braced(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m(boolean c1, boolean c2, boolean c3) {
                        if (c1) callRule2_%s();
                        for (int i = 0; i < 3; i++) callRule2_%s();
                        while (c2) callRule2_%s();
                        do callRule2_%s(); while (c3);
                    }
                    void callRule2_%s() {}
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier);

        String out = formatter(100, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("if (c1) {");
        assertThat(out).contains("for (int i = 0; i < 3; i++) {");
        assertThat(out).contains("while (c2) {");
        assertThat(out).contains("do {");
    }

    @Property(tries = 60)
    void rule2_opening_braces_use_kr_style(@ForAll("identifier") String identifier) {
        String source = """
                class Rule2_%s{
                void m(boolean c){if(c){call();}else{call();}}
                void call(){}
                }
                """.formatted(identifier);

        String out = formatter(120, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("class Rule2_" + identifier + " {");
        assertThat(out).contains("void m(boolean c) {");
        assertThat(out).contains("if (c) {");
        assertThat(out).doesNotContainPattern("\\)\\s*\\n\\s*\\{");
    }

    // Rule 3: Indentation
    @Property(tries = 80)
    void rule3_wrapped_binary_continuation_uses_two_indent_steps(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    boolean m(boolean a, boolean b, boolean c) {
                        return condition_%s(a, b, c) && anotherCondition_%s(a, b, c) && thirdCondition_%s(a, b, c);
                    }
                    boolean condition_%s(boolean a, boolean b, boolean c) { return a; }
                    boolean anotherCondition_%s(boolean a, boolean b, boolean c) { return b; }
                    boolean thirdCondition_%s(boolean a, boolean b, boolean c) { return c; }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier, identifier);

        String out = formatter(60, WrapStyle.BALANCED, true, 4).format(source);
        String returnLine = lineContaining(out, "return condition_");
        String operatorLine = lineContaining(out, "&& anotherCondition_");
        assertThat(leadingSpaces(operatorLine)).isEqualTo(leadingSpaces(returnLine) + 8);
    }

    @Property(tries = 80)
    void rule3_wrapped_method_chain_uses_single_indent_step(@ForAll("identifier") String identifier) {
        String source = """
                import java.util.List;
                class C {
                    List<String> m(List<String> items) {
                        return items.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
                    }
                }
                """;
        String out = formatter(60, WrapStyle.BALANCED, true, 4).format(source);
        String base = lineContaining(out, "return items");
        String chain = lineContaining(out, ".stream()");
        assertThat(leadingSpaces(chain)).isEqualTo(leadingSpaces(base) + 4);
    }

    // Rule 4: Line length and wrapping trigger
    @Property(tries = 60)
    void rule4_small_line_length_triggers_wrapping(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m() {
                        veryLongInvocationRule4_%s(longArgOne_%s, longArgTwo_%s, longArgThree_%s, longArgFour_%s, longArgFive_%s);
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier, identifier);
        String out = formatter(60, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("veryLongInvocationRule4_" + identifier + "(\n");
    }

    @Property(tries = 60)
    void rule4_large_line_length_keeps_equivalent_shape_more_inline(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m() {
                        veryLongInvocationRule4_%s(longArgOne_%s, longArgTwo_%s, longArgThree_%s);
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier);
        String out = formatter(220, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("veryLongInvocationRule4_" + identifier + "(longArgOne_");
    }

    // Rule 5: WrapStyle must be construct-uniform
    @Property(tries = 80)
    void rule5_balanced_wraps_arguments_one_per_line_when_overflow(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m() {
                        callRule5_%s(longArgOne_%s, longArgTwo_%s, longArgThree_%s, longArgFour_%s);
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier);
        String out = formatter(60, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("longArgOne_" + identifier + ",\n");
        assertThat(out).contains("longArgTwo_" + identifier + ",\n");
    }

    @Property(tries = 80)
    void rule5_narrow_wraps_array_initializer_one_per_line_when_overflow(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    String[] m() {
                        return new String[] {"%s_one", "%s_two", "%s_three", "%s_four", "%s_five"};
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier);
        String out = formatter(60, WrapStyle.NARROW, true, 4).format(source);
        assertThat(out).contains("{\n");
        assertThat(out).contains("\"" + identifier + "_one\",\n");
        assertThat(out).contains("\"" + identifier + "_two\",\n");
    }

    // Rule 6: Wrapped binary/operator chains
    @Property(tries = 80)
    void rule6_wrapped_binary_places_operator_at_line_start(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    boolean m(boolean a, boolean b, boolean c) {
                        return one_%s(a, b, c) && two_%s(a, b, c) && three_%s(a, b, c);
                    }
                    boolean one_%s(boolean a, boolean b, boolean c) { return a; }
                    boolean two_%s(boolean a, boolean b, boolean c) { return b; }
                    boolean three_%s(boolean a, boolean b, boolean c) { return c; }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier, identifier);
        String out = formatter(55, WrapStyle.BALANCED, true, 4).format(source);
        String line = lineContaining(out, "&& two_");
        assertThat(line.stripLeading()).startsWith("&& ");
    }

    @Property(tries = 80)
    void rule6_wrapped_plus_chain_places_operator_at_line_start(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    String m() {
                        return "prefix_%s_" + "middle_%s_" + "suffix_%s_" + "tail_%s_";
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier);
        String out = formatter(40, WrapStyle.BALANCED, true, 4).format(source);
        String plusLine = lineContaining(out, "+ \"middle_");
        assertThat(plusLine.stripLeading()).startsWith("+ ");
    }

    // Rule 7: Method chains
    @Property(tries = 80)
    void rule7_wrapped_chain_segments_start_with_dot(@ForAll("identifier") String identifier) {
        String source = """
                import java.util.List;
                class C {
                    List<String> m(List<String> values) {
                        return values.stream().map(String::trim).filter(v -> !v.isBlank()).map(String::toUpperCase).toList();
                    }
                }
                """;
        String out = formatter(65, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(lineContaining(out, ".stream()").stripLeading()).startsWith(".");
        assertThat(lineContaining(out, ".map(String::trim)").stripLeading()).startsWith(".");
        assertThat(lineContaining(out, ".toList()").stripLeading()).startsWith(".");
    }

    @Property(tries = 60)
    void rule7_chain_under_binary_operand_gets_extra_indent(@ForAll("identifier") String identifier) {
        String source = """
                import java.util.List;
                class C {
                    boolean m(List<String> values) {
                        return values != null && values.stream().map(String::trim).filter(v -> !v.isBlank()).findAny().isPresent();
                    }
                }
                """;
        String out = formatter(70, WrapStyle.BALANCED, true, 4).format(source);
        String operatorLine = lineContaining(out, "&& values");
        String chainLine = lineContaining(out, ".stream()");
        assertThat(leadingSpaces(chainLine)).isEqualTo(leadingSpaces(operatorLine) + 4);
    }

    // Rule 8: Closing delimiter placement
    @Property(tries = 80)
    void rule8_closing_paren_on_new_line_true_uses_own_line_for_wrapped_calls(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m() {
                        callRule8_%s(longArgOne_%s, longArgTwo_%s, longArgThree_%s, longArgFour_%s);
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier);
        String out = formatter(60, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).containsPattern("\\n\\s*\\)\\s*;\\n");
    }

    @Property(tries = 80)
    void rule8_closing_paren_on_new_line_false_keeps_closer_inline(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    void m() {
                        callRule8_%s(longArgOne_%s, longArgTwo_%s, longArgThree_%s, longArgFour_%s);
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier, identifier);
        String out = formatter(60, WrapStyle.BALANCED, false, 4).format(source);
        assertThat(out).contains("longArgFour_" + identifier + ");");
    }

    // Rule 9: Blank lines
    @Property(tries = 80)
    void rule9_collapses_multiple_blank_lines_to_at_most_one(@ForAll("blankRun") int blankRun) {
        String source = "class C {\n" + "\n".repeat(blankRun) + "    void m() {\n" + "\n".repeat(blankRun) + "        int x = 1;\n" + "\n".repeat(blankRun) + "    }\n" + "\n".repeat(blankRun) + "}\n";
        String out = formatter(120, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).doesNotContain("\n\n\n");
    }

    @Property(tries = 80)
    void rule9_no_blank_line_immediately_after_open_or_before_close_brace(@ForAll("identifier") String identifier) {
        String source = """
                class C {

                    void m() {

                        call_%s();

                    }
                }
                """.formatted(identifier);
        String out = formatter(120, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).doesNotContain("{\n\n");
        assertThat(out).doesNotContain("\n\n    }");
    }

    // Rule 10: Comments and annotation safety (expressible subset)
    @Property(tries = 80)
    void rule10_preserves_comment_markers_count(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    // lead_%s
                    void m() {
                        /* block_%s */
                        call_%s(); // trail_%s
                    }
                }
                """.formatted(identifier, identifier, identifier, identifier);
        String out = formatter(80, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(count(source, "//")).isEqualTo(count(out, "//"));
        assertThat(count(source, "/*")).isEqualTo(count(out, "/*"));
    }

    @Property(tries = 60)
    void rule10_preserves_type_use_annotation_position(@ForAll("identifier") String identifier) {
        String source = """
                class C {
                    @interface Nullable {}
                    public @Nullable String value_%s() { return "x"; }
                }
                """.formatted(identifier);
        String out = formatter(120, WrapStyle.BALANCED, true, 4).format(source);
        assertThat(out).contains("public @Nullable String value_" + identifier + "()");
    }

    @Provide
    Arbitrary<String> identifier() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(4)
                .ofMaxLength(8);
    }

    @Provide
    Arbitrary<Integer> argumentCount() {
        return Arbitraries.integers().between(3, 8);
    }

    @Provide
    Arbitrary<Boolean> closingParenOnNewLine() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Integer> blankRun() {
        return Arbitraries.integers().between(2, 6);
    }

    private static String longArgs(int count, String seed) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("longArgumentRule1_").append(seed).append("_").append(i);
        }
        return sb.toString();
    }

    private static Formatter formatter(int lineLength, WrapStyle wrapStyle, boolean closeOnNewLine, int indentSize) {
        return new Formatter(
                FormatterConfig.builder()
                        .lineLength(lineLength)
                        .wrapStyle(wrapStyle)
                        .closingParenOnNewLine(closeOnNewLine)
                        .indentSize(indentSize)
                        .javaLanguageLevel(JavaLanguageLevel.of(17))
                        .build());
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static String lineContaining(String text, String needle) {
        String[] lines = text.split("\\R", -1);
        for (String line : lines) {
            if (line.contains(needle)) {
                return line;
            }
        }
        throw new AssertionError("Missing line containing: " + needle);
    }

    private static int count(String s, String token) {
        int idx = 0;
        int n = 0;
        while (true) {
            int at = s.indexOf(token, idx);
            if (at < 0) {
                return n;
            }
            n++;
            idx = at + token.length();
        }
    }
}
