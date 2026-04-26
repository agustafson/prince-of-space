package io.princeofspace.internal;

import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumConstantWrappingTest {

    private static final String INPUT = """
            class T {
                enum HttpStatus {
                    OK(200, "OK"), CREATED(201, "Created"), BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), INTERNAL_SERVER_ERROR(500, "Internal Server Error");
                }
            }
            """;

    @Test
    void enumConstants_balanced_oneConstantPerLineWhenOverflow() {
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.BALANCED)
                                .build());

        String out = formatter.format(INPUT);

        assertThat(out).contains("        OK(200, \"OK\"),\n");
        assertThat(out).contains("        CREATED(201, \"Created\"),\n");
        assertThat(out).contains("        BAD_REQUEST(400, \"Bad Request\"),\n");
        assertThat(formatter.format(out)).isEqualTo(out);
    }

    @Test
    void enumConstants_wide_greedyPacking() {
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.WIDE)
                                .build());

        String out = formatter.format(INPUT);

        assertThat(out)
                .contains(
                        "        OK(200, \"OK\"), CREATED(201, \"Created\"), BAD_REQUEST(400, \"Bad Request\"), UNAUTHORIZED(401, \"Unauthorized\"),\n");
        assertThat(formatter.format(out)).isEqualTo(out);
    }

    @Test
    void enumConstants_narrow_oneConstantPerLine() {
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .lineLength(120)
                                .wrapStyle(WrapStyle.NARROW)
                                .build());

        String out = formatter.format(INPUT);

        assertThat(out).contains("        OK(200, \"OK\"),\n");
        assertThat(out).contains("        CREATED(201, \"Created\"),\n");
        assertThat(out).contains("        BAD_REQUEST(400, \"Bad Request\"),\n");
        assertThat(formatter.format(out)).isEqualTo(out);
    }
}
