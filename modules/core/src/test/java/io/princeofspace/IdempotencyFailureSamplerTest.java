package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast local triage for idempotency failures.
 *
 * <p>Env vars:
 *
 * <ul>
 *   <li>PRINCE_SAMPLE_ROOT: required project root (e.g. /Users/gus/dev/projects/spring-framework)
 *   <li>PRINCE_SAMPLE_LIST: required newline-delimited relative file list
 *   <li>PRINCE_SAMPLE_CONFIG: one of aggressive-wide/aggressive-balanced/aggressive-narrow/
 *       moderate-wide/moderate-balanced/moderate-narrow/default-wide/default-balanced/default-narrow
 *   <li>PRINCE_SAMPLE_LIMIT: optional positive integer (default 50)
 * </ul>
 */
class IdempotencyFailureSamplerTest {

    @Test
    void sampleFailures_printFirstDiffPerFile() throws Exception {
        String rootRaw = System.getenv("PRINCE_SAMPLE_ROOT");
        String listRaw = System.getenv("PRINCE_SAMPLE_LIST");
        String configRaw = System.getenv("PRINCE_SAMPLE_CONFIG");
        if (isBlank(rootRaw) || isBlank(listRaw) || isBlank(configRaw)) {
            System.out.println("Set PRINCE_SAMPLE_ROOT, PRINCE_SAMPLE_LIST, PRINCE_SAMPLE_CONFIG");
            return;
        }
        Path root = Path.of(rootRaw);
        Path listPath = Path.of(listRaw);
        assertThat(Files.isDirectory(root)).isTrue();
        assertThat(Files.isRegularFile(listPath)).isTrue();

        Formatter formatter = new Formatter(configFromName(configRaw).toFormatterConfig());
        int limit = parseLimit(System.getenv("PRINCE_SAMPLE_LIMIT"));
        List<String> relPaths = Files.readAllLines(listPath).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted(Comparator.naturalOrder())
                .toList();

        int checked = 0;
        int failed = 0;
        List<String> outputs = new ArrayList<>();
        for (String rel : relPaths) {
            if (checked >= limit) {
                break;
            }
            Path file = root.resolve(rel);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            checked++;
            String source = Files.readString(file);
            String once;
            try {
                once = formatter.format(source);
            } catch (RuntimeException ex) {
                outputs.add("PARSE_FAIL " + rel + " :: " + ex.getMessage());
                continue;
            }
            String twice = formatter.format(once);
            if (!once.equals(twice)) {
                failed++;
                outputs.add("FAIL " + rel + " :: " + firstDiffSummary(once, twice));
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("SAMPLED checked=")
                .append(checked)
                .append(" failed=")
                .append(failed)
                .append(" config=")
                .append(configRaw)
                .append('\n');
        for (String line : outputs) {
            report.append(line).append('\n');
        }
        String outRaw = System.getenv("PRINCE_SAMPLE_OUT");
        if (!isBlank(outRaw)) {
            Files.writeString(Path.of(outRaw), report.toString());
        }
        System.out.print(report);
    }

    private static int parseLimit(String raw) {
        if (isBlank(raw)) {
            return 50;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : 50;
        } catch (NumberFormatException ex) {
            return 50;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstDiffSummary(String once, String twice) {
        String[] a = once.split("\n", -1);
        String[] b = twice.split("\n", -1);
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            String left = i < a.length ? a[i] : "<EOF>";
            String right = i < b.length ? b[i] : "<EOF>";
            if (!left.equals(right)) {
                String leftPrev = i > 0 && i - 1 < a.length ? a[i - 1] : "<BOF>";
                String rightPrev = i > 0 && i - 1 < b.length ? b[i - 1] : "<BOF>";
                String leftNext = i + 1 < a.length ? a[i + 1] : "<EOF>";
                String rightNext = i + 1 < b.length ? b[i + 1] : "<EOF>";
                return "line "
                        + (i + 1)
                        + " | prev< "
                        + leftPrev
                        + " | prev> "
                        + rightPrev
                        + " | < "
                        + left
                        + " | > "
                        + right
                        + " | next< "
                        + leftNext
                        + " | next> "
                        + rightNext;
            }
        }
        return "outputs differ";
    }

    private record EvalConfig(String name, int lineLength, WrapStyle wrapStyle) {
        FormatterConfig toFormatterConfig() {
            return FormatterConfig.builder()
                    .lineLength(lineLength)
                    .wrapStyle(wrapStyle)
                    .build();
        }
    }

    private static EvalConfig configFromName(String name) {
        return switch (name) {
            case "aggressive-wide" -> new EvalConfig(name, 80, WrapStyle.WIDE);
            case "aggressive-balanced" -> new EvalConfig(name, 80, WrapStyle.BALANCED);
            case "aggressive-narrow" -> new EvalConfig(name, 80, WrapStyle.NARROW);
            case "moderate-wide" -> new EvalConfig(name, 100, WrapStyle.WIDE);
            case "moderate-balanced" -> new EvalConfig(name, 100, WrapStyle.BALANCED);
            case "moderate-narrow" -> new EvalConfig(name, 100, WrapStyle.NARROW);
            case "default-wide" -> new EvalConfig(name, 120, WrapStyle.WIDE);
            case "default-balanced" -> new EvalConfig(name, 120, WrapStyle.BALANCED);
            case "default-narrow" -> new EvalConfig(name, 120, WrapStyle.NARROW);
            default -> throw new IllegalArgumentException("Unknown config: " + name);
        };
    }
}
