package io.princeofspace.internal;

import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
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
    void arrayInitializerWithInteriorBlockComment_convergesToTallLayout() {
        FormattingEngine eng = new FormattingEngine(FormatterConfig.defaults());
        String src =
                "class T {\n"
                + "    static final String[] X = {\"alpha\", /* sep */ \"beta\", \"gamma\"};\n"
                + "}\n";
        FormatResult result = eng.format(src);
        assertThat(result).isInstanceOf(FormatResult.Success.class);
        String out = ((FormatResult.Success) result).formattedSource();
        // Convergence proves the layout is stable; previously this oscillated and never converged.
        assertThat(((FormatResult.Success) eng.format(out)).formattedSource()).isEqualTo(out);
    }

    @Test
    void arrayInitializer_leadingLineCommentPlusOwnedComment_converges() {
        // Mirrors Spring ControllerAdviceBeanTests: two // lines before the first element — JavaParser
        // may treat one as an orphan sibling and one as the element's owned comment across passes.
        FormatterConfig cfg =
                FormatterConfig.builder().lineLength(120).wrapStyle(WrapStyle.BALANCED).build();
        FormattingEngine eng = new FormattingEngine(cfg);
        String src =
                "class T {\n"
                        + "    void m() {\n"
                        + "        Class<?>[] xs = {\n"
                        + "                // First explains dependency ordering.\n"
                        + "                // Second clarifies implementation detail.\n"
                        + "                String.class,\n"
                        + "                Integer.class,\n"
                        + "        };\n"
                        + "    }\n"
                        + "}\n";
        FormatResult result = eng.format(src);
        assertThat(result).isInstanceOf(FormatResult.Success.class);
        String once = ((FormatResult.Success) result).formattedSource();
        assertThat(((FormatResult.Success) eng.format(once)).formattedSource()).isEqualTo(once);
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

    @Test
    void maxConvergencePassesZero_returnsNonConvergentWhenFirstPassChangesOutput() {
        FormattingEngine onePassOnly = new FormattingEngine(FormatterConfig.defaults(), 0);
        FormatResult result = onePassOnly.format("class T {void m(){int x=1;}}");
        assertThat(result).isInstanceOf(FormatResult.NonConvergent.class);
    }

    @Test
    void interpretMaxConvergencePasses_blankUsesDefault() {
        int defaultPasses = FormattingEngine.interpretMaxConvergencePasses(null);
        assertThat(FormattingEngine.interpretMaxConvergencePasses("")).isEqualTo(defaultPasses);
        assertThat(FormattingEngine.interpretMaxConvergencePasses("   ")).isEqualTo(defaultPasses);
    }

    @Test
    void interpretMaxConvergencePasses_validInteger() {
        assertThat(FormattingEngine.interpretMaxConvergencePasses("0")).isZero();
        assertThat(FormattingEngine.interpretMaxConvergencePasses(" 7 ")).isEqualTo(7);
    }

    @Test
    void interpretMaxConvergencePasses_negativeClampsToZero() {
        assertThat(FormattingEngine.interpretMaxConvergencePasses("-1")).isZero();
    }

    @Test
    void interpretMaxConvergencePasses_nonNumericFallsBackToDefault() {
        assertThat(FormattingEngine.interpretMaxConvergencePasses("twelve"))
                .isEqualTo(FormattingEngine.interpretMaxConvergencePasses(null));
    }
}
