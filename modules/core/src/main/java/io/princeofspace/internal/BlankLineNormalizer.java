package io.princeofspace.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-processes formatted output to enforce blank-line rules:
 *
 * <ol>
 *   <li>No blank line immediately after a line that ends with <code>{</code>.
 *   <li>No blank line immediately before a line that starts with <code>}</code>.
 *   <li>No consecutive blank lines (collapse to at most one).
 * </ol>
 *
 * <p><b>Note:</b> Rule 1 is applied uniformly in Phase 2 because {@code DefaultPrettyPrinter}
 * always places <code>{</code> at the end of a declaration line (K&amp;R style). Phase 4's custom
 * printer, which may place <code>{</code> on its own line for long type declarations, will revisit
 * this rule.
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

        // Rule 1: no blank immediately after "{", EXCEPT after class/interface/enum/record opening braces
        String prevLine = prevNonBlank;
        if (prevLine.endsWith("{") && !isTypeDeclarationBrace(emitted)) {
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

    private static boolean isTypeDeclarationBrace(List<String> emitted) {
        int last = lastNonBlankIndex(emitted, emitted.size() - 1);
        if (last < 0) {
            return false;
        }
        String line = emitted.get(last).trim();
        if (!line.endsWith("{")) {
            return false;
        }
        if (looksLikeTypeDeclarationHeader(emitted.get(last))) {
            return true;
        }
        for (int i = last - 1; i >= 0; i--) {
            String rawCandidate = emitted.get(i);
            String candidate = rawCandidate.trim();
            if (candidate.isEmpty()) {
                break;
            }
            if (candidate.endsWith("{")) {
                break;
            }
            if (looksLikeTypeDeclarationHeader(rawCandidate)) {
                return true;
            }
            if (candidate.endsWith(";") || candidate.endsWith("}")) {
                break;
            }
        }
        return false;
    }

    private static int lastNonBlankIndex(List<String> lines, int startInclusive) {
        for (int i = startInclusive; i >= 0; i--) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * True for a line that opens a class, interface, enum, or record body, including nested type
     * declarations (indentation allowed).
     */
    private static boolean looksLikeTypeDeclarationHeader(String line) {
        String trimmedLine = line.trim();
        return trimmedLine.contains(" class ")
                || trimmedLine.startsWith("class ")
                || trimmedLine.contains(" interface ")
                || trimmedLine.startsWith("interface ")
                || trimmedLine.contains(" enum ")
                || trimmedLine.startsWith("enum ")
                || trimmedLine.contains(" record ")
                || trimmedLine.startsWith("record ");
    }
}
