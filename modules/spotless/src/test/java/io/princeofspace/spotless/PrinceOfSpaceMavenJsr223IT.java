package io.princeofspace.spotless;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end check that prince-of-space formats Java through spotless-maven-plugin's
 * {@code <jsr223>} step. Gated on {@code PRINCE_MAVEN_IT=true} because it shells out to a
 * real Maven and downloads spotless-maven-plugin + groovy from Maven Central.
 */
class PrinceOfSpaceMavenJsr223IT {

    @Test
    void mavenSpotlessApplyFormatsAndIsIdempotent(@TempDir Path work) throws Exception {
        assumeTrue("true".equals(System.getenv("PRINCE_MAVEN_IT")),
                "set PRINCE_MAVEN_IT=true to run the Maven end-to-end test");

        // Copy the fixture project into a writable temp dir.
        Path fixture = Path.of("src/test/resources/maven-it");
        try (Stream<Path> paths = Files.walk(fixture)) {
            paths.forEach(src -> copy(src, work.resolve(fixture.relativize(src))));
        }
        Path sample = work.resolve("src/main/java/it/Sample.java");

        // First apply: must reformat the file.
        runApply(work);
        String formatted = Files.readString(sample);
        assertThat(formatted).contains("int x = 1;");
        assertThat(formatted).doesNotContain("int x=1;");

        // Second apply: idempotent — file unchanged.
        runApply(work);
        assertThat(Files.readString(sample)).isEqualTo(formatted);
    }

    private static void runApply(Path projectDir) throws Exception {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(projectDir.resolve("pom.xml").toFile());
        request.setGoals(singletonList("spotless:apply"));
        request.setBatchMode(true);
        Invoker invoker = new DefaultInvoker();
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null) {
            invoker.setMavenHome(new File(mavenHome));
        }
        InvocationResult result = invoker.execute(request);
        if (result.getExecutionException() != null) {
            throw result.getExecutionException();
        }
        assertThat(result.getExitCode()).as("mvn spotless:apply exit code").isZero();
    }

    private static void copy(Path src, Path dest) {
        try {
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
            } else {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
