package io.princeofspace.model;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaParserLanguageLevelsTest {

    @Test
    void fromRelease_mapsLegacyAndModern() {
        assertThat(JavaParserLanguageLevels.fromRelease(1)).isEqualTo(LanguageLevel.JAVA_1_0);
        assertThat(JavaParserLanguageLevels.fromRelease(8)).isEqualTo(LanguageLevel.JAVA_8);
        assertThat(JavaParserLanguageLevels.fromRelease(25)).isEqualTo(LanguageLevel.JAVA_25);
    }

    @Test
    void fromRelease_unknownModernThrows() {
        assertThatThrownBy(() -> JavaParserLanguageLevels.fromRelease(999999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JAVA_999999")
                .hasMessageContaining("javaparser");
    }
}
