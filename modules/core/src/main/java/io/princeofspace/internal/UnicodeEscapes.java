package io.princeofspace.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.GeneratedJavaParserConstants.CHARACTER_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.JAVADOC_COMMENT;
import static com.github.javaparser.GeneratedJavaParserConstants.MULTI_LINE_COMMENT;
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
        boolean oddBlockBackslashes = false;
        int previousStarOffset = -1;
        int blockStarOffset = -1;
        boolean previousWasEscape = false;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            int decodedOffset = decoded == null ? index : decoded.length();
            boolean unicodeStart = value == '\\'
                    && index + 1 < source.length() && source.charAt(index + 1) == 'u';
            boolean eligible = previousWasEscape || !oddBackslashes;
            boolean ordinaryAmbiguity = eligible != !oddRawBackslashes;
            boolean blockAmbiguity = blockStarOffset >= 0 && eligible != !oddBlockBackslashes;
            if (unicodeStart && (ordinaryAmbiguity || blockAmbiguity)) {
                if (ambiguities == null) {
                    ambiguities = new ArrayList<>();
                }
                ambiguities.add(new Ambiguity(index, decodedOffset, ordinaryAmbiguity ? -1 : blockStarOffset));
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
            // JDK 8/11 reads the first character after a block-comment '*' without pairing raw backslashes.
            if (!escape && value == '\\') {
                if (previousStarOffset >= 0) {
                    oddBlockBackslashes = false;
                    blockStarOffset = previousStarOffset;
                } else {
                    oddBlockBackslashes = !oddBlockBackslashes;
                }
            } else {
                oddBlockBackslashes = false;
                blockStarOffset = -1;
            }
            previousStarOffset = value == '*' ? decodedOffset : -1;
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
                    // The initial Javadoc star uses scanCommentChar; later stars use scanChar.
                    boolean applies = ambiguity.blockStarOffset() < 0
                            || ((token.getKind() == MULTI_LINE_COMMENT || token.getKind() == JAVADOC_COMMENT)
                                    && !(token.getKind() == JAVADOC_COMMENT
                                            && ambiguity.blockStarOffset() == offset + "/*".length()));
                    // An escape can create a comment terminator or a literal delimiter itself.
                    if (applies && (delimiter == 0 || ambiguity.decodedOffset() < offset + delimiter
                            || ambiguity.decodedOffset() >= end - delimiter)) {
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

    record Ambiguity(int rawOffset, int decodedOffset, int blockStarOffset) {

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
