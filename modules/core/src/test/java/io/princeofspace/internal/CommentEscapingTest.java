package io.princeofspace.internal;

import com.github.javaparser.Providers;
import com.github.javaparser.UnicodeEscapeProcessingProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommentEscapingTest {

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("decodedComments")
    void encodedContentRetainsDecodedCharactersAndConverges(String decoded) throws IOException {
        String escaped = CommentUtils.escapeCommentContent(decoded);

        assertThat(StandardCharsets.UTF_8.newEncoder().canEncode(CharBuffer.wrap(escaped))).isTrue();
        assertThat(escaped).doesNotContain("\0");
        assertThat(decode(escaped)).isEqualTo(decoded);
        assertThat(CommentUtils.escapeCommentContent(decode(escaped))).isEqualTo(escaped);
    }

    static Stream<Arguments> decodedComments() {
        List<Arguments> cases = new ArrayList<>();
        String high = Character.toString(0xD801);
        String low = Character.toString(0xDC01);
        for (int backslashes = 0; backslashes <= 5; backslashes++) {
            for (String suffix : List.of("uXXXX", "uDC01", "u000a", "u002a/", "u002f", "uuuuXXXX",
                    high, low, "\0", high + low, "path", "")) {
                cases.add(Arguments.of("\\".repeat(backslashes) + suffix));
            }
        }
        cases.add(Arguments.of(high + "\\uDC01"));
        cases.add(Arguments.of(low + "\\uD801"));
        cases.add(Arguments.of(high + "\\uXXXX" + low));
        return cases.stream();
    }

    private static String decode(String source) throws IOException {
        StringBuilder decoded = new StringBuilder();
        try (var provider = new UnicodeEscapeProcessingProvider(Providers.provider(source))) {
            char[] buffer = new char[32];
            for (int count = provider.read(buffer, 0, buffer.length);
                    count != -1;
                    count = provider.read(buffer, 0, buffer.length)) {
                decoded.append(buffer, 0, count);
            }
        }
        return decoded.toString();
    }
}
