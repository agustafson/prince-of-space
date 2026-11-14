package io.princeofspace;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Executable;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationPreservationTest {
    private static final String FIXTURE = "/annotations/AnnotationPlacement.java";
    private static final String SOURCE_NAME = "AnnotationPlacement.java";
    private static final String TARGET_RELEASE = "17";
    private static final int NARROW_WIDTH = 60;
    private static final int WIDE_WIDTH = 120;

    @TempDir
    Path temporaryDirectory;

    @Test
    void methodDeclarationAnnotations_printOnceBeforeNullableReturn() {
        String input = """
                class Example {
                    @Deprecated @SuppressWarnings({"unchecked", "rawtypes"})
                    public @org.jspecify.annotations.Nullable String find(String key) { return key; }
                }
                """;
        Formatter formatter = new Formatter(FormatterConfig.defaults());

        String output = formatter.format(input);

        assertThat(output).containsOnlyOnce("@Deprecated");
        assertThat(output).containsOnlyOnce("@SuppressWarnings");
        assertThat(output).contains("public @org.jspecify.annotations.Nullable String find");
        assertThat(formatter.format(output)).isEqualTo(output);
    }

    @ParameterizedTest
    @EnumSource(WrapStyle.class)
    void annotationCountsAndReflectedTargets_surviveFormatting(WrapStyle style) throws Exception {
        String input;
        try (var resource = AnnotationPreservationTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(resource).isNotNull();
            input = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> annotations = annotationNames(input);
        List<String> targets = compiledAnnotations(compile(input, temporaryDirectory.resolve("input")));
        assertThat(targets).anyMatch(target -> target.contains("@org.jspecify.annotations.Nullable"));

        for (int width : new int[] {NARROW_WIDTH, WIDE_WIDTH}) {
            for (boolean singlePass : new boolean[] {false, true}) {
                Formatter formatter = new Formatter(FormatterConfig.builder()
                        .wrapStyle(style)
                        .lineLength(width)
                        .build(), singlePass);

                String output = formatter.format(input);

                assertThat(annotationNames(output)).isEqualTo(annotations);
                Path directory = temporaryDirectory.resolve(style + "-" + width + "-" + singlePass);
                assertThat(compiledAnnotations(compile(output, directory))).isEqualTo(targets);
                assertThat(formatter.format(output)).isEqualTo(output);
            }
        }
    }

    private static List<String> annotationNames(String source) {
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.RAW));
        var parsed = parser.parse(source);
        assertThat(parsed.isSuccessful()).as("fixture parses: %s", parsed.getProblems()).isTrue();
        return parsed.getResult().orElseThrow().findAll(AnnotationExpr.class).stream()
                .map(AnnotationExpr::getNameAsString)
                .sorted()
                .toList();
    }

    private static Path compile(String source, Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve(SOURCE_NAME);
        Files.writeString(file, source);
        Path classes = Files.createDirectory(directory.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            String annotationsJar = Path.of(Nullable.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            boolean compiled = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", TARGET_RELEASE, "-proc:none", "-g:none", "-classpath", annotationsJar, "-d", classes.toString()),
                    null, manager.getJavaFileObjects(file)).call();
            assertThat(compiled).as("javac diagnostics: %s", diagnostics.getDiagnostics()).isTrue();
        }
        return classes;
    }

    private static List<String> compiledAnnotations(Path classes) throws Exception {
        var snapshot = new ArrayList<String>();
        URL annotationsJar = Nullable.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL(), annotationsJar}, ClassLoader.getPlatformClassLoader());
                var files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = classes.relativize(file).toString().replace(File.separatorChar, '.');
                String name = relative.substring(0, relative.length() - ".class".length());
                Class<?> type = Class.forName(name, false, loader);
                annotations(snapshot, name, type);
                typeParameters(snapshot, name, type.getTypeParameters());
                annotatedType(snapshot, name + "/superclass", type.getAnnotatedSuperclass());
                AnnotatedType[] interfaces = type.getAnnotatedInterfaces();
                for (int i = 0; i < interfaces.length; i++) {
                    annotatedType(snapshot, name + "/interface/" + i, interfaces[i]);
                }
                for (var field : type.getDeclaredFields()) {
                    String owner = name + "/field/" + field.getName();
                    annotations(snapshot, owner, field);
                    annotatedType(snapshot, owner, field.getAnnotatedType());
                }
                for (Executable executable : Stream.concat(Arrays.stream(type.getDeclaredConstructors()),
                        Arrays.stream(type.getDeclaredMethods())).toList()) {
                    String owner = name + "/" + executable.toGenericString();
                    annotations(snapshot, owner, executable);
                    typeParameters(snapshot, owner, executable.getTypeParameters());
                    annotatedType(snapshot, owner + "/return", executable.getAnnotatedReturnType());
                    annotatedType(snapshot, owner + "/receiver", executable.getAnnotatedReceiverType());
                    var parameters = executable.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        annotations(snapshot, owner + "/parameter/" + i, parameters[i]);
                        annotatedType(snapshot, owner + "/parameter/" + i, parameters[i].getAnnotatedType());
                    }
                    AnnotatedType[] exceptions = executable.getAnnotatedExceptionTypes();
                    for (int i = 0; i < exceptions.length; i++) {
                        annotatedType(snapshot, owner + "/throws/" + i, exceptions[i]);
                    }
                }
                if (type.isRecord()) {
                    for (var component : type.getRecordComponents()) {
                        String owner = name + "/component/" + component.getName();
                        annotations(snapshot, owner, component);
                        annotatedType(snapshot, owner, component.getAnnotatedType());
                    }
                }
            }
        }
        Collections.sort(snapshot);
        return snapshot;
    }

    private static void annotations(List<String> snapshot, String owner, AnnotatedElement element) {
        snapshot.add(owner + "/annotations=" + Arrays.toString(element.getDeclaredAnnotations()));
    }

    private static void typeParameters(List<String> snapshot, String owner, TypeVariable<?>[] parameters) {
        for (TypeVariable<?> parameter : parameters) {
            String target = owner + "/type-parameter/" + parameter.getName();
            annotations(snapshot, target, parameter);
            AnnotatedType[] bounds = parameter.getAnnotatedBounds();
            for (int i = 0; i < bounds.length; i++) {
                annotatedType(snapshot, target + "/bound/" + i, bounds[i]);
            }
        }
    }

    private static void annotatedType(List<String> snapshot, String owner, @Nullable AnnotatedType type) {
        if (type == null) {
            return;
        }
        annotations(snapshot, owner + "/" + type.getType().getTypeName(), type);
        annotatedType(snapshot, owner + "/owner", type.getAnnotatedOwnerType());
        if (type instanceof AnnotatedArrayType array) {
            annotatedType(snapshot, owner + "/element", array.getAnnotatedGenericComponentType());
        } else if (type instanceof AnnotatedParameterizedType parameterized) {
            AnnotatedType[] arguments = parameterized.getAnnotatedActualTypeArguments();
            for (int i = 0; i < arguments.length; i++) {
                annotatedType(snapshot, owner + "/argument/" + i, arguments[i]);
            }
        } else if (type instanceof AnnotatedWildcardType wildcard) {
            AnnotatedType[] upper = wildcard.getAnnotatedUpperBounds();
            for (int i = 0; i < upper.length; i++) {
                annotatedType(snapshot, owner + "/upper/" + i, upper[i]);
            }
            AnnotatedType[] lower = wildcard.getAnnotatedLowerBounds();
            for (int i = 0; i < lower.length; i++) {
                annotatedType(snapshot, owner + "/lower/" + i, lower[i]);
            }
        }
    }
}
