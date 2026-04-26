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
    private static final String IMPORT_PREFIX = "import ";

    private BlankLineNormalizer() {}

    static String normalize(String source) {
        // Pre-scan: build an index of line start offsets so we can inspect
        // previous/next non-blank lines without splitting or trimming.
        int len = source.length();
        // lineStarts[i] = char offset where line i begins; lineEnds[i] = exclusive end (before \n).
        // We over-allocate slightly then trim.
        int estimatedLines = len / AVG_CHARS_PER_LINE_ESTIMATE + 1;
        int[] lineStarts = new int[estimatedLines];
        int[] lineEnds = new int[estimatedLines];
        int lineCount = 0;
        int pos = 0;
        while (pos <= len) {
            int nl = source.indexOf('\n', pos);
            if (nl < 0) {
                nl = len;
            }
            if (lineCount == lineStarts.length) {
                lineStarts = grow(lineStarts);
                lineEnds = grow(lineEnds);
            }
            lineStarts[lineCount] = pos;
            lineEnds[lineCount] = nl;
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
            if (!isBlankLine(source, lineStarts[i], lineEnds[i])) {
                keep[i] = true;
                lastKeptNonBlank = i;
                lastKeptWasBlank = false;
            } else {
                keep[i] = shouldKeepBlank(
                        source, lineStarts, lineEnds, lineCount, i, lastKeptNonBlank, lastKeptWasBlank);
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
            sb.append(source, lineStarts[i], lineEnds[i]);
        }
        return sb.toString();
    }

    private static boolean shouldKeepBlank(
            String source, int[] lineStarts, int[] lineEnds, int lineCount,
            int blankIndex, int lastKeptNonBlank, boolean lastKeptWasBlank) {

        // Rule 3: no consecutive blank lines
        if (lastKeptWasBlank) {
            return false;
        }

        // Find next non-blank line (forward scan from blankIndex+1).
        int nextNonBlank = -1;
        for (int j = blankIndex + 1; j < lineCount; j++) {
            if (!isBlankLine(source, lineStarts[j], lineEnds[j])) {
                nextNonBlank = j;
                break;
            }
        }

        // Preserve spacing groups inside import sections.
        if (lastKeptNonBlank >= 0 && nextNonBlank >= 0) {
            boolean prevIsImport = isImportLine(source, lineStarts[lastKeptNonBlank], lineEnds[lastKeptNonBlank]);
            boolean nextIsImport = isImportLine(source, lineStarts[nextNonBlank], lineEnds[nextNonBlank]);
            if (prevIsImport && nextIsImport) {
                return true;
            }
        }

        // Rule 1: no blank immediately after "{"
        if (lastKeptNonBlank >= 0
                && trimmedEndsWith(source, lineStarts[lastKeptNonBlank], lineEnds[lastKeptNonBlank], '{')) {
            return false;
        }

        // Rule 2: no blank immediately before "}"
        if (nextNonBlank >= 0
                && trimmedStartsWith(source, lineStarts[nextNonBlank], lineEnds[nextNonBlank], '}')) {
            return false;
        }

        return true;
    }

    private static boolean isBlankLine(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isImportLine(String s, int start, int end) {
        // Skip leading whitespace then check for "import ".
        int i = start;
        while (i < end && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return end - i >= IMPORT_PREFIX.length()
                && s.regionMatches(i, IMPORT_PREFIX, 0, IMPORT_PREFIX.length());
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

    private static int[] grow(int[] arr) {
        int[] bigger = new int[arr.length * 2];
        System.arraycopy(arr, 0, bigger, 0, arr.length);
        return bigger;
    }
}
