package io.princeofspace.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-processes formatted output to enforce blank-line rules:
 *
 * <ol>
 *   <li>No blank line immediately after a line that ends with <code>{</code> (including type bodies).
 *   <li>No blank line immediately before a line that starts with <code>}</code>.
 *   <li>No consecutive blank lines (collapse to at most one).
 * </ol>
 *
 */
final class BlankLineNormalizer {

    private BlankLineNormalizer() {}

    static String normalize(String source) {
        String[] input = source.split("\n", -1);
        List<String> output = new ArrayList<>(input.length);

        for (int i = 0; i < input.length; i++) {
            String line = input[i];
            if (line.trim().isEmpty()) {
                if (shouldKeep(output, input, i)) {
                    output.add(line);
                }
            } else {
                output.add(line);
            }
        }

        return String.join("\n", output);
    }

    private static boolean shouldKeep(List<String> emitted, String[] input, int blankIndex) {
        // Preserve spacing groups inside import sections.
        String prevNonBlank = lastNonBlank(emitted).trim();
        String nextNonBlank = firstNonBlankAfter(input, blankIndex + 1).trim();
        if (isImportLine(prevNonBlank) && isImportLine(nextNonBlank)) {
            return true;
        }

        // Rule 1: no blank immediately after "{"
        String prevLine = prevNonBlank;
        if (prevLine.endsWith("{")) {
            return false;
        }

        // Rule 2: no blank immediately before "}"
        if (nextNonBlank.startsWith("}")) {
            return false;
        }

        // Rule 3: no consecutive blank lines
        if (!emitted.isEmpty() && emitted.get(emitted.size() - 1).trim().isEmpty()) {
            return false;
        }

        return true;
    }

    private static boolean isImportLine(String trimmedLine) {
        return trimmedLine.startsWith("import ");
    }

    private static String lastNonBlank(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).trim().isEmpty()) {
                return lines.get(i);
            }
        }
        return "";
    }

    private static String firstNonBlankAfter(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                return lines[i];
            }
        }
        return "";
    }

}
