package io.princeofspace.internal;

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

    private static final int AVG_CHARS_PER_LINE_ESTIMATE = 40;
    private static final String IMPORT_KEYWORD = "import";

    private BlankLineNormalizer() {}

    static String normalize(String source) {
        // Pre-scan: build an index of line start offsets so we can inspect
        // previous/next non-blank lines without splitting or trimming.
        int len = source.length();
        // Paired entries: bounds[2*i] = line start, bounds[2*i+1] = exclusive end (before \n).
        int estimatedLines = len / AVG_CHARS_PER_LINE_ESTIMATE + 1;
        int[] lineBounds = new int[estimatedLines * 2];
        int lineCount = 0;
        int pos = 0;
        while (pos <= len) {
            int nl = source.indexOf('\n', pos);
            if (nl < 0) {
                nl = len;
            }
            if (lineCount * 2 >= lineBounds.length) {
                lineBounds = growLineBounds(lineBounds);
            }
            setLine(lineBounds, lineCount, pos, nl);
            lineCount++;
            pos = nl + 1;
            if (nl == len) {
                break;
            }
        }

        // Decide which lines to keep. A line is "blank" when it contains only whitespace.
        boolean[] keep = new boolean[lineCount];
        // Track last kept non-blank line index for Rule 1 and consecutive-blank checks.
        int lastKeptNonBlank = -1;
        boolean lastKeptWasBlank = false;
        for (int i = 0; i < lineCount; i++) {
            if (!isBlankLine(source, lineBounds, i)) {
                keep[i] = true;
                lastKeptNonBlank = i;
                lastKeptWasBlank = false;
            } else {
                keep[i] = shouldKeepBlank(
                        source, lineBounds, lineCount, i, lastKeptNonBlank, lastKeptWasBlank);
                if (keep[i]) {
                    lastKeptWasBlank = true;
                }
            }
        }

        // Build output — single StringBuilder pass, no intermediate String[] or List.
        StringBuilder sb = new StringBuilder(len);
        boolean first = true;
        for (int i = 0; i < lineCount; i++) {
            if (!keep[i]) {
                continue;
            }
            if (!first) {
                sb.append('\n');
            }
            first = false;
            sb.append(source, lineStart(lineBounds, i), lineEnd(lineBounds, i));
        }
        return sb.toString();
    }

    private static int lineStart(int[] bounds, int lineIndex) {
        return bounds[lineIndex * 2];
    }

    private static int lineEnd(int[] bounds, int lineIndex) {
        return bounds[lineIndex * 2 + 1];
    }

    private static void setLine(int[] bounds, int lineIndex, int start, int end) {
        bounds[lineIndex * 2] = start;
        bounds[lineIndex * 2 + 1] = end;
    }

    private static int[] growLineBounds(int[] arr) {
        int[] bigger = new int[arr.length * 2];
        System.arraycopy(arr, 0, bigger, 0, arr.length);
        return bigger;
    }

    private static boolean shouldKeepBlank(
            String source,
            int[] lineBounds,
            int lineCount,
            int blankIndex,
            int lastKeptNonBlank,
            boolean lastKeptWasBlank) {

        // Rule 3: no consecutive blank lines
        if (lastKeptWasBlank) {
            return false;
        }

        // Find next non-blank line (forward scan from blankIndex+1).
        int nextNonBlank = -1;
        for (int j = blankIndex + 1; j < lineCount; j++) {
            if (!isBlankLine(source, lineBounds, j)) {
                nextNonBlank = j;
                break;
            }
        }

        // Preserve spacing groups inside import sections.
        if (lastKeptNonBlank >= 0 && nextNonBlank >= 0) {
            boolean prevIsImport =
                    isImportLine(source, lineStart(lineBounds, lastKeptNonBlank), lineEnd(lineBounds, lastKeptNonBlank));
            boolean nextIsImport =
                    isImportLine(source, lineStart(lineBounds, nextNonBlank), lineEnd(lineBounds, nextNonBlank));
            if (prevIsImport && nextIsImport) {
                return true;
            }
        }

        // Rule 1: no blank immediately after "{"
        if (lastKeptNonBlank >= 0
                && trimmedEndsWith(
                        source, lineStart(lineBounds, lastKeptNonBlank), lineEnd(lineBounds, lastKeptNonBlank), '{')) {
            return false;
        }

        // Rule 2: no blank immediately before "}"
        if (nextNonBlank >= 0
                && trimmedStartsWith(
                        source, lineStart(lineBounds, nextNonBlank), lineEnd(lineBounds, nextNonBlank), '}')) {
            return false;
        }

        return true;
    }

    private static boolean isBlankLine(String s, int[] bounds, int lineIndex) {
        int start = lineStart(bounds, lineIndex);
        int end = lineEnd(bounds, lineIndex);
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isImportLine(String s, int start, int end) {
        // Skip leading whitespace; JLS allows any whitespace between `import` and the name.
        int i = start;
        while (i < end && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int keywordLen = IMPORT_KEYWORD.length();
        if (end - i < keywordLen + 1) {
            return false;
        }
        if (!s.regionMatches(i, IMPORT_KEYWORD, 0, keywordLen)) {
            return false;
        }
        return Character.isWhitespace(s.charAt(i + keywordLen));
    }

    private static boolean trimmedEndsWith(String s, int start, int end, char c) {
        for (int i = end - 1; i >= start; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return s.charAt(i) == c;
            }
        }
        return false;
    }

    private static boolean trimmedStartsWith(String s, int start, int end, char c) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return s.charAt(i) == c;
            }
        }
        return false;
    }
}
