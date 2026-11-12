package io.princeofspace;

import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LambdaVarFormattingTest {

    private static final String INPUT = """
            import java.util.function.Function;
            import java.util.function.IntUnaryOperator;
            import org.jspecify.annotations.Nullable;

            class Example {
                Function<String, String> annotated = (@Nullable var value) -> value;
                Function<String, String> plain = (var value) -> value;
                IntUnaryOperator[] operators = {(var value) -> value};
            }
            """;

    @ParameterizedTest
    @ValueSource(ints = {11, 17, 21, 25})
    void inferredLambdaParametersInFieldInitializers_keepAnnotations(int release) {
        FormatterConfig config = FormatterConfig.builder()
                .javaLanguageLevel(JavaLanguageLevel.of(release))
                .build();
        for (boolean singlePass : new boolean[] {false, true}) {
            Formatter formatter = new Formatter(config, singlePass);

            String output = formatter.format(INPUT);

            assertThat(output).containsOnlyOnce("@Nullable");
            assertThat(output).contains("(@Nullable var value)", "(var value)");
            assertThat(formatter.format(output)).isEqualTo(output);
        }
    }

    @Test
    void inferredLambdaParametersBeforeJava11_remainRejected() {
        Formatter formatter = new Formatter(FormatterConfig.builder()
                .javaLanguageLevel(JavaLanguageLevel.of(10))
                .build());

        assertThat(formatter.formatResult(INPUT)).isInstanceOf(FormatResult.ParseFailure.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "class Example { var field = 1; }",
        "class Example { var method() { return 1; } }",
        "class Example { void method(var value) {} }",
        "class Example { void method() { var value = null; } }",
        "class Example { void method() { var value; } }",
        "class Example { void method() { var[] value = new int[0]; } }",
        "class Example { java.util.function.Function<String, String> field = (var value) -> ; }",
        "class Example { var field = (var value) -> value; }",
        "class Example { java.util.function.BiFunction<String, String, String> field = (var left, String right) -> left; }",
        "class Example { java.util.function.BiFunction<String, String, String> field = (var left, right) -> left; }",
        "class Example { java.util.function.Function<String[], String[]> field = (var... values) -> values; }",
        "class Example { java.util.function.Function<String[], String[]> field = (var values[]) -> values; }",
        "class Example { java.util.function.Function<String, String> field = (var value) -> { var local; return value; }; }",
        "class Example { java.util.function.Function<String, String> field = (var value) -> { var local = null; return value; }; }",
        "class Example { java.util.function.Function<String, String> field = (var value) -> { var local = {1}; return value; }; }"
    })
    void invalidVarDeclarations_remainRejected(String input) {
        assertThat(new Formatter(FormatterConfig.defaults()).formatResult(input))
                .isInstanceOf(FormatResult.ParseFailure.class);
    }

    @Test
    void fieldVarArrayWithLambda_retainsItsOwnValidationProblems() {
        String input = "class Example { var values[] = {(java.util.function.IntUnaryOperator) (var value) -> value}; }";
        Formatter formatter = new Formatter(FormatterConfig.defaults());
        var baseline = formatter.formatResult("class Example { var values[] = {1}; }");
        assertThat(baseline).isInstanceOf(FormatResult.ParseFailure.class);
        var result = formatter.formatResult(input);

        assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class, failure -> {
            assertThat(failure.problemMessages())
                    .filteredOn(message -> message.contains("\"var\" cannot have extra array brackets."))
                    .hasSize(1);
            assertThat(failure.problemMessages())
                    .isEqualTo(((FormatResult.ParseFailure) baseline).problemMessages());
        });
    }
}
