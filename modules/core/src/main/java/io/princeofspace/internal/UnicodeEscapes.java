package io.princeofspace.internal;

import static java.util.HexFormat.fromHexDigits;

/** Translates Unicode escapes before lexing, using the eligibility rules in JLS 3.3. */
final class UnicodeEscapes {

    private static final int HEX_DIGITS = 4;

    private UnicodeEscapes() {}

    static String decode(String source) throws MalformedEscapeException {
        if (!source.contains("\\u")) {
            return source;
        }

        StringBuilder decoded = null;
        boolean oddBackslashes = false;
        boolean previousWasEscape = false;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            boolean escape = value == '\\' && (previousWasEscape || !oddBackslashes)
                    && index + 1 < source.length() && source.charAt(index + 1) == 'u';
            if (escape) {
                int start = index;
                int digits = index + 2;
                while (digits < source.length() && source.charAt(digits) == 'u') {
                    digits++;
                }
                if (source.length() - digits < HEX_DIGITS) {
                    throw new MalformedEscapeException(start);
                }
                try {
                    value = (char) fromHexDigits(source, digits, digits + HEX_DIGITS);
                } catch (NumberFormatException malformed) {
                    throw new MalformedEscapeException(start);
                }
                if (decoded == null) {
                    decoded = new StringBuilder(source.length()).append(source, 0, start);
                }
                index = digits + HEX_DIGITS - 1;
            }
            if (decoded != null) {
                decoded.append(value);
            }
            // Translated backslashes count in the run; their provenance also makes the next one eligible.
            oddBackslashes = value == '\\' && !oddBackslashes;
            previousWasEscape = escape;
        }
        return decoded == null ? source : decoded.toString();
    }

    static final class MalformedEscapeException extends Exception {
        private static final long serialVersionUID = 1L;

        MalformedEscapeException(int offset) {
            super("Invalid Unicode escape at source offset " + offset);
        }
    }
}
