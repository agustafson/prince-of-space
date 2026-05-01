package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class BundledArtifactTest {

    @Test
    void bundledFormatter_outputMatchesCore_onSamples() throws Exception {
        Path jar = bundledJar();
        Formatter core = new Formatter(FormatterConfig.defaults());
        List<String> samples =
                List.of(
                        "class T { void m() { int x = 1; } }",
                        """
                        class T {
                            void m() {
                                if (x) a(); else b();
                            }
                        }
                        """);
        for (String src : samples) {
            String expected = core.format(src);
            assertThat(formatWithBundledJar(jar, src)).isEqualTo(expected);
        }
    }

    @Test
    void bundledFormatter_isIdempotent() throws Exception {
        Path jar = bundledJar();
        String src =
                """
                class T {
                    @Override @Deprecated
                    void m() {}
                }
                """;
        String once = formatWithBundledJar(jar, src);
        assertThat(formatWithBundledJar(jar, once)).isEqualTo(once);
    }

    @Test
    void shadedJar_onlyContainsProjectAndMetaInfEntries() throws Exception {
        Path jar = bundledJar();
        List<String> bad = new ArrayList<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (name.endsWith("/")) {
                    continue;
                }
                if (!(name.startsWith("io/princeofspace/") || name.startsWith("META-INF/"))) {
                    bad.add(name);
                }
            }
        }
        assertThat(bad)
                .as("Bundled jar must only contain io/princeofspace/ and META-INF/ entries; "
                    + "any other root indicates an un-relocated dependency leaked through the shadow plugin (found: %s)", bad)
                .isEmpty();
    }

    private static Path bundledJar() {
        String p = System.getProperty("bundled.jar.path");
        assertThat(p).as("bundled.jar.path system property (set by Gradle test task)").isNotNull();
        return Path.of(p);
    }

    private static String formatWithBundledJar(Path jar, String source) throws Exception {
        URL[] urls = {jar.toUri().toURL()};
        try (URLClassLoader cl =
                new URLClassLoader("bundled-isolated", urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> configClass = cl.loadClass("io.princeofspace.model.FormatterConfig");
            Object defaults = configClass.getMethod("defaults").invoke(null);
            Class<?> formatterClass = cl.loadClass("io.princeofspace.Formatter");
            Object formatter = formatterClass.getConstructor(configClass).newInstance(defaults);
            Method format = formatterClass.getMethod("format", String.class);
            return (String) format.invoke(formatter, source);
        }
    }
}
