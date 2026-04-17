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

    @Test
    void toLanguageLevel_standardReleases() {
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(8))).isEqualTo(LanguageLevel.JAVA_8);
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(17))).isEqualTo(LanguageLevel.JAVA_17);
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(21))).isEqualTo(LanguageLevel.JAVA_21);
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(25))).isEqualTo(LanguageLevel.JAVA_25);
    }

    @Test
    void toLanguageLevel_legacyReleases() {
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(1))).isEqualTo(LanguageLevel.JAVA_1_0);
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(7))).isEqualTo(LanguageLevel.JAVA_7);
    }

    @Test
    void toLanguageLevel_previewRelease() {
        // Java 17 preview — JavaParser 3.28.0 defines JAVA_17_PREVIEW (preview variants exist up to 17)
        LanguageLevel result = JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(17, true));
        assertThat(result).isEqualTo(LanguageLevel.JAVA_17_PREVIEW);
    }

    @Test
    void toLanguageLevel_unknownReleaseThrows() {
        assertThatThrownBy(() -> JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(999999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JAVA_999999");
    }
}
