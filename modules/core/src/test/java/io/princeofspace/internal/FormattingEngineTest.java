package io.princeofspace.internal;

import io.princeofspace.FormatResult;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormattingEngineTest {

    private final FormattingEngine engine = new FormattingEngine(FormatterConfig.defaults());

    // ── success ──────────────────────────────────────────────────────────────

    @Test
    void validSource_returnsSuccess() {
        FormatResult result = engine.format("class T {}");
        assertThat(result).isInstanceOf(FormatResult.Success.class);
    }

    @Test
    void success_formattedSourceIsNotBlank() {
        FormatResult.Success success = (FormatResult.Success) engine.format("class T {}");
        assertThat(success.formattedSource()).isNotBlank();
    }

    @Test
    void success_outputContainsOriginalClassName() {
        FormatResult.Success success = (FormatResult.Success) engine.format("class MyClass {}");
        assertThat(success.formattedSource()).contains("MyClass");
    }

    @Test
    void success_isIdempotent() {
        String once = ((FormatResult.Success) engine.format("class T { void m(){} }")).formattedSource();
        String twice = ((FormatResult.Success) engine.format(once)).formattedSource();
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void multipleMembers_successAndIdempotent() {
        String src = """
                class T {
                int x;
                void m() { if (x > 0) x--; }
                }
                """;
        String once = ((FormatResult.Success) engine.format(src)).formattedSource();
        String twice = ((FormatResult.Success) engine.format(once)).formattedSource();
        assertThat(twice).isEqualTo(once);
    }

    // ── parse failure ─────────────────────────────────────────────────────────

    @Test
    void syntacticallyInvalidSource_returnsParseFailure() {
        FormatResult result = engine.format("class {{{");
        assertThat(result).isInstanceOf(FormatResult.ParseFailure.class);
    }

    @Test
    void parseFailure_problemMessagesNotEmpty() {
        FormatResult.ParseFailure failure = (FormatResult.ParseFailure) engine.format("class {{{");
        assertThat(failure.problemMessages()).isNotEmpty();
    }

    @Test
    void parseFailure_messageDescribesError() {
        FormatResult.ParseFailure failure = (FormatResult.ParseFailure) engine.format("this is not java");
        assertThat(failure.message()).startsWith("Parse failed:");
    }

    // ── convergence ───────────────────────────────────────────────────────────

    @Test
    void chainedMethodCalls_convergesWithinTwoPasses() {
        // A non-trivial input that the engine must stabilise through convergence passes.
        String src = """
                class T {
                    void m(java.util.List<String> items) {
                        String result = items.stream().filter(s -> s.startsWith("x")).map(s -> s.toLowerCase()).collect(java.util.stream.Collectors.joining(", "));
                    }
                }
                """;
        FormatResult result = engine.format(src);
        assertThat(result).isInstanceOf(FormatResult.Success.class);
        String once = ((FormatResult.Success) result).formattedSource();
        String twice = ((FormatResult.Success) engine.format(once)).formattedSource();
        assertThat(twice).isEqualTo(once);
    }
}
