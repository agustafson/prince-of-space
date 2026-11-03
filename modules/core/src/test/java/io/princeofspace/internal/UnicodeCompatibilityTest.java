package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatResult;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static io.princeofspace.internal.CompilerAssertions.compileAndInvoke;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Keep the raw source fixtures readable.
class UnicodeCompatibilityTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {8, 11, 17, 21, 25})
    void rejectsCompilerDependentCommentBoundaries(int release) {
        String ambiguous = "\\u005C" + "\\\\" + "u000A";
        String source = "class T { void run() { int value = 0;\n// " + ambiguous + " value = 1;\n} }";
        int offset = source.indexOf(ambiguous) + 7;
        FormatterConfig config = FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(release)).build();
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(config, singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains(
                            "Compiler-dependent Unicode escape", "source offset " + offset, "JDK 8/11", "JDK 17+"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousSources")
    void rejectsAmbiguitiesOutsideLiteralContent(String name, String source, int expectedOffset) {
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(FormatterConfig.defaults(), singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).isEqualTo(
                            "Parse failed:\nCompiler-dependent Unicode escape at source offset " + expectedOffset
                                    + " outside literal content: JDK 8/11 and JDK 17+ can interpret it differently"));
        }
    }

    static Stream<Arguments> ambiguousSources() {
        String forward = "\\u005C" + "\\" + "|" + "\\";
        String reverse = "\\u005C" + "\\\\" + "|" + "\\";
        String rawPair = "\\" + "|" + "\\";
        String rawTriple = "\\\\" + "|" + "\\";
        return Stream.of(
                ambiguous("LF ends comment", "class T { void run() { int s = 0;\n// " + forward + "u000A s=1;\n} }"),
                ambiguous("LF stays in comment", "class T { void run() { int s = 0;\n// " + reverse + "u000A s=1;\n} }"),
                ambiguous("CR ends comment", "class T {} // " + forward + "u000D int s;"),
                ambiguous("CR stays in comment", "class T {} // " + reverse + "u000D int s;"),
                ambiguous("star closes block comment", "class T {} /* " + forward + "u002A/ // */"),
                ambiguous("star stays in block comment", "class T {} /* " + reverse + "u002A/ // */"),
                ambiguous("slash closes block comment", "class T {} /* " + forward + "u002A\\u002F // */"),
                ambiguous("repeated u", "class T {} // " + forward + "uuuu000A"),
                ambiguous("malformed only under legacy interpretation", "class T {} // " + reverse + "uZZZZ"),
                ambiguous("harmless comment text", "class T {} // " + forward + "u0078"),
                ambiguous("comment-only source", "// " + reverse + "u000A"),
                ambiguous("Javadoc", "/** " + reverse + "u000A */ class T {}"),
                ambiguous("string closing delimiter", "class T { String value = \"" + forward + "u0022; }"),
                ambiguous("char closing delimiter", "class T { char value = '" + forward + "u0027; }"),
                ambiguous("text block closing delimiter", "class T { String value = \"\"\"\n" + forward + "u0022\"\"; }"),
                ambiguous("raw pair at block opening", "class T { /*" + rawPair + "u002A/ int x; // */\n }"),
                ambiguous("raw triple after block star", "class T { /* a *" + rawTriple + "u002A/ int x; // */\n }"),
                ambiguous("raw pair after escaped star", "class T { /* a \\u002A" + rawPair + "u002A/ int x; // */\n }"),
                ambiguous("raw pair after Javadoc body star", "class T { /** a *" + rawPair + "u002A/ int x; // */\n }"),
                ambiguous("raw pair after extra Javadoc star", "class T { /***" + rawPair + "u002A/ int x; // */\n }"),
                ambiguous("malformed after block opening", "class T { /*" + rawPair + "uZZZZ */ }"),
                ambiguous("literal before ambiguity", "class T { String value = \"" + forward.replace("|", "") + "u006e\"; } // "
                        + forward + "u000A"));
    }

    private static Arguments ambiguous(String name, String markedSource) {
        int offset = markedSource.indexOf('|');
        assertThat(offset).isNotNegative();
        assertThat(markedSource.indexOf('|', offset + 1)).isEqualTo(-1);
        return Arguments.of(name, markedSource.substring(0, offset) + markedSource.substring(offset + 1), offset);
    }

    @Test
    void includesAmbiguityOffsetAlongsideSyntaxProblems() {
        String escape = "\\u005C" + "\\\\" + "u000a";
        String source = "class T {} // " + escape + " garbage";
        int expectedOffset = source.indexOf(escape) + 7;
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(FormatterConfig.defaults(), singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains("Parse error",
                            "Compiler-dependent Unicode escape at source offset " + expectedOffset));
        }
    }

    @Test
    void keepsLiteralAmbiguitiesExemptWhenOtherSyntaxFails() {
        String escape = "\\u005C" + "\\\\" + "u006e";
        String source = "class T { String value = \"" + escape + "\"; int ; }";
        for (boolean singlePass : new boolean[] {true, false}) {
            FormatResult result = new Formatter(FormatterConfig.defaults(), singlePass).formatResult(source);
            assertThat(result).isInstanceOfSatisfying(FormatResult.ParseFailure.class,
                    failure -> assertThat(failure.message()).contains("Parse error")
                            .doesNotContain("Compiler-dependent Unicode escape"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("literalPrograms")
    void permitsMixedEscapesInsideLiterals(String name, String source) throws Exception {
        String expected = compileAndInvoke(directory.resolve("before"), source);
        for (boolean singlePass : new boolean[] {true, false}) {
            Formatter formatter = new Formatter(FormatterConfig.defaults(), singlePass);
            String output = formatter.format(source);
            assertThat(compileAndInvoke(directory.resolve("after-" + singlePass), output)).isEqualTo(expected);
            if (!singlePass) {
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    static Stream<Arguments> literalPrograms() {
        String forward = "\\u005C" + "\\\\";
        String reverse = "\\u005C" + "\\\\\\";
        String prefix = "public class EscapeProbe { public static String value() { ";
        String suffix = " } }";
        return Stream.of(
                Arguments.of("forward string", prefix + "return \"" + forward + "u006e\";" + suffix),
                Arguments.of("reverse string", prefix + "return \"" + reverse + "u006e\";" + suffix),
                Arguments.of("ineligible malformed string", prefix + "return \"" + reverse + "uZZZZ\";" + suffix),
                Arguments.of("multiple literals", prefix + "return \"" + forward + "u006e\" + \"" + reverse + "u006e\";" + suffix),
                Arguments.of("text block", prefix + "return \"\"\"\n" + forward + "u006e\n" + reverse + "u006e\n\"\"\";" + suffix),
                Arguments.of("star before string backslashes", prefix + "return \"*\\\\u006e\";" + suffix),
                Arguments.of("star before text block backslashes", prefix + "return \"\"\"\n*\\\\u006e\n\"\"\";" + suffix),
                Arguments.of("leading trivia and CRLF", "\t// header\r\n" + prefix + "return \"" + forward + "u006e\";" + suffix),
                Arguments.of("generics and shifts", prefix + "java.util.List<java.util.List<String>> list = null; "
                        + "int n = 8 >>> 1; return \"" + forward + "u006e\" + (list == null ? n : 0);" + suffix));
    }

    static Stream<Arguments> portableComments() {
        String escapes = "\\u005C".repeat(2) + "u002A/ int hidden; // ";
        return Stream.of("/*", "/* a *", "/**", "/** a *", "// *").map(prefix -> Arguments.of(prefix,
                "public class EscapeProbe {\n" + prefix + escapes
                + (prefix.startsWith("//") ? "\n" : "*/\n")
                + "public static String value() { return String.valueOf(EscapeProbe.class.getDeclaredFields().length); } "
                + "public static void main(String[] args) { System.out.print(value()); } }"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portableComments")
    void preservesPortableCommentBackslashes(String name, String source) throws Exception {
        assertThat(compileAndInvoke(directory.resolve("before"), source)).isEqualTo("0");
        for (boolean singlePass : new boolean[] {true, false}) {
            Formatter formatter = new Formatter(FormatterConfig.defaults(), singlePass);
            String output = formatter.format(source);
            assertThat(output).contains("\\u005C\\u005Cu002A");
            assertThat(compileAndInvoke(directory.resolve("after-" + singlePass), output)).isEqualTo("0");
            if (!singlePass) {
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portableComments")
    @EnabledIfEnvironmentVariable(named = "PRINCE_LEGACY_JAVA_HOMES", matches = ".+")
    void preservesPortableCommentsWithLegacyCompilers(String name, String source) throws Exception {
        int compilerIndex = 0;
        for (String home : System.getenv("PRINCE_LEGACY_JAVA_HOMES").split(",", -1)) {
            Path javac = Path.of(home).resolve("bin/javac");
            Path compilerDirectory = directory.resolve("compiler-" + compilerIndex);
            compilerIndex++;
            assertThat(compileAndInvoke(compilerDirectory.resolve("before"), source, javac)).isEqualTo("0");
            for (boolean singlePass : new boolean[] {true, false}) {
                Formatter formatter = new Formatter(FormatterConfig.defaults(), singlePass);
                String output = formatter.format(source);
                assertThat(compileAndInvoke(compilerDirectory.resolve("after-" + singlePass), output, javac))
                        .isEqualTo("0");
                if (!singlePass) {
                    assertThat(formatter.format(output)).isEqualTo(output);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/**", "/* a ", "// *"})
    void permitsOrdinaryRawPairsAfterJavadocOpenersAndLineCommentStars(String prefix) {
        String source = "class T {\n" + prefix + "\\\\u002A/ int x; // "
                + (prefix.startsWith("//") ? "\n" : "*/\n") + "}";
        Formatter formatter = new Formatter(FormatterConfig.defaults());
        String output = formatter.format(source);
        assertThat(output).contains("\\u005C\\u005Cu002A/ int x;");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @Test
    void permitsRawPairsAtAFileStartingJavadocOpener() {
        String source = "/**\\\\u002A/ int x; // */ class T {}";
        Formatter formatter = new Formatter(FormatterConfig.defaults());
        String output = formatter.format(source);
        assertThat(output).contains("\\u005C\\u005Cu002A/ int x;");
        assertThat(formatter.format(output)).isEqualTo(output);
    }
}
