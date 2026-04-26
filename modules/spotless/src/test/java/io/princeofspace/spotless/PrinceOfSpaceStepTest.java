package io.princeofspace.spotless;

import com.diffplug.spotless.FormatterStep;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrinceOfSpaceStepTest {

    @Test
    void formatsJavaSource(@TempDir Path dir) throws Exception {
        try (FormatterStep step = PrinceOfSpaceStep.create(FormatterConfig.defaults())) {
            File file = dir.resolve("T.java").toFile();
            String input = "class T { void m() { int x=1;} }";
            String out = step.format(input, file);
            assertThat(out).contains("int x = 1;");
            assertThat(step.format(out, file)).isEqualTo(out);
        }
    }

    @Test
    void stepIsSerializableRoundtrip() throws Exception {
        byte[] serialized;
        try (FormatterStep step = PrinceOfSpaceStep.create(FormatterConfig.defaults());
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(step);
            serialized = bos.toByteArray();
        }
        try (java.io.ObjectInputStream ois =
                new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(serialized));
                FormatterStep read = (FormatterStep) ois.readObject();
                FormatterStep expected = PrinceOfSpaceStep.create(FormatterConfig.defaults())) {
            File file = File.createTempFile("roundtrip", ".java");
            String input = "class A {}";
            assertThat(read.format(input, file)).isEqualTo(expected.format(input, file));
        }
    }
}
