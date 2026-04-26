package io.princeofspace.cli;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void helpExitsZero() {
        int code = new CommandLine(new Main()).execute("--help");
        assertThat(code).isZero();
    }

    @Test
    void unknownJavaVersionExits2() {
        int code = new CommandLine(new Main()).execute("--java-version", "999999");
        assertThat(code).isEqualTo(2);
    }

    @Test
    void stdinRoundTrip() {
        String input = "class T { void m() { int x=1;} }";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.InputStream oldIn = System.in;
        PrintStream oldOut = System.out;
        System.setIn(in);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new Main()).execute("--stdin");
            assertThat(code).isZero();
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("int x = 1;");
    }

    @Test
    void checkMode_reportsCleanForFormattedFile(@TempDir Path dir) throws Exception {
        String src = "class T {}\n";
        Path f = dir.resolve("T.java");
        Files.writeString(f, src);
        String formatted = new Formatter(FormatterConfig.defaults()).format(src);
        Files.writeString(f, formatted);

        int code = new CommandLine(new Main()).execute("--check", f.toString());
        assertThat(code).isZero();
    }

    @Test
    void checkMode_exitsOneWhenChangeNeeded(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("T.java");
        Files.writeString(f, "class T {void m(){int x=1;}}");

        int code = new CommandLine(new Main()).execute("--check", f.toString());
        assertThat(code).isEqualTo(1);
    }

    @Test
    void findGitRoot_detectsGitdirPointerFile(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve(".git"), "gitdir: " + dir.resolve("real.git") + "\n");
        assertThat(Main.findGitRoot(repo)).isEqualTo(repo);
    }

    @Test
    void collectJavaFiles_includesUntrackedJavaUnderGitRoot(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        Files.createDirectories(repo);
        ProcessBuilder init = new ProcessBuilder("git", "init");
        init.directory(repo.toFile());
        assertThat(init.start().waitFor()).isZero();

        Path tracked = repo.resolve("Tracked.java");
        Path untracked = repo.resolve("Untracked.java");
        Files.writeString(tracked, "class Tracked {}\n");
        Files.writeString(untracked, "class Untracked {}\n");

        ProcessBuilder add = new ProcessBuilder("git", "add", "Tracked.java");
        add.directory(repo.toFile());
        assertThat(add.start().waitFor()).isZero();
        ProcessBuilder commit =
                new ProcessBuilder(
                        "git",
                        "-c",
                        "user.email=test@example.com",
                        "-c",
                        "user.name=Test",
                        "commit",
                        "-m",
                        "init");
        commit.directory(repo.toFile());
        assertThat(commit.start().waitFor()).isZero();

        List<Path> files = Main.collectJavaFiles(List.of(repo), false);
        assertThat(files).containsExactlyInAnyOrder(tracked, untracked);
    }

    @Test
    void collectJavaFiles_nonRecursiveInGitRoot_excludesNestedJava(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        Path nestedDir = repo.resolve("src/nested");
        Files.createDirectories(nestedDir);
        ProcessBuilder init = new ProcessBuilder("git", "init");
        init.directory(repo.toFile());
        assertThat(init.start().waitFor()).isZero();

        Path topLevel = repo.resolve("Top.java");
        Path nested = nestedDir.resolve("Nested.java");
        Files.writeString(topLevel, "class Top {}\n");
        Files.writeString(nested, "class Nested {}\n");

        List<Path> files = Main.collectJavaFiles(List.of(repo), false);
        assertThat(files).containsExactly(topLevel);
    }
}
