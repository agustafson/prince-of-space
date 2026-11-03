package io.princeofspace.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.GeneratedJavaParserConstants.CHARACTER_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.STRING_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.TEXT_BLOCK_LITERAL;
import static java.util.HexFormat.fromHexDigits;

/** Translates Unicode escapes before lexing, using the eligibility rules in JLS 3.3. */
final class UnicodeEscapes {

    private static final int HEX_DIGITS = 4;

    private UnicodeEscapes() {}

    static DecodedSource decode(String source) throws MalformedEscapeException {
        if (!source.contains("\\u")) {
            return new DecodedSource(source, List.of());
        }

        StringBuilder decoded = null;
        List<Ambiguity> ambiguities = null;
        boolean oddBackslashes = false;
        boolean oddRawBackslashes = false;
        boolean previousWasEscape = false;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            boolean unicodeStart = value == '\\'
                    && index + 1 < source.length() && source.charAt(index + 1) == 'u';
            boolean eligible = previousWasEscape || !oddBackslashes;
            if (unicodeStart && eligible != !oddRawBackslashes) {
                if (ambiguities == null) {
                    ambiguities = new ArrayList<>();
                }
                ambiguities.add(new Ambiguity(index, decoded == null ? index : decoded.length()));
            }
            boolean escape = unicodeStart && eligible;
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
            // Historical comment scanning pairs raw backslashes independently of translated ones.
            oddRawBackslashes = !escape && value == '\\' && !oddRawBackslashes;
            previousWasEscape = escape;
        }
        return new DecodedSource(decoded == null ? source : decoded.toString(),
                ambiguities == null ? List.of() : ambiguities);
    }

    record DecodedSource(String source, List<Ambiguity> ambiguities) {

        Optional<String> ambiguityProblem(TokenRange tokens) {
            if (ambiguities.isEmpty()) {
                return Optional.empty();
            }
            int index = 0;
            int offset = 0;
            for (JavaToken token : tokens.getBegin().toTokenRange()) {
                int end = offset + token.getText().length();
                int delimiter = switch (token.getKind()) {
                    case CHARACTER_LITERAL, STRING_LITERAL -> 1;
                    case TEXT_BLOCK_LITERAL -> "\"\"\"".length();
                    default -> 0;
                };
                while (index < ambiguities.size() && ambiguities.get(index).decodedOffset() < end) {
                    Ambiguity ambiguity = ambiguities.get(index);
                    // An escape can create a comment terminator or a literal delimiter itself.
                    if (delimiter == 0 || ambiguity.decodedOffset() < offset + delimiter
                            || ambiguity.decodedOffset() >= end - delimiter) {
                        return Optional.of(ambiguity.problem());
                    }
                    index++;
                }
                if (index == ambiguities.size()) {
                    return Optional.empty();
                }
                offset = end;
            }
            return Optional.of(ambiguities.get(index).problem());
        }
    }

    record Ambiguity(int rawOffset, int decodedOffset) {

        String problem() {
            return "Compiler-dependent Unicode escape at source offset " + rawOffset
                    + " outside literal content: JDK 8/11 and JDK 17+ can interpret it differently";
        }
    }

    static final class MalformedEscapeException extends Exception {
        private static final long serialVersionUID = 1L;

        MalformedEscapeException(int offset) {
            super("Invalid Unicode escape at source offset " + offset);
        }
    }
}
