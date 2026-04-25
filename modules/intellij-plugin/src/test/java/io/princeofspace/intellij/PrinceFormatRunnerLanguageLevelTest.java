package io.princeofspace.intellij;

import com.intellij.pom.java.LanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link PrinceFormatRunner} helpers (no full IDE fixture required). */
class PrinceFormatRunnerLanguageLevelTest {

    @Test
    void intellijLanguageLevelToRelease_mapsJdk1xStyle() {
        assertThat(PrinceFormatRunner.intellijLanguageLevelToRelease(LanguageLevel.JDK_1_6)).isEqualTo(6);
        assertThat(PrinceFormatRunner.intellijLanguageLevelToRelease(LanguageLevel.JDK_1_8)).isEqualTo(8);
    }

    @Test
    void intellijLanguageLevelToRelease_mapsModernJdkStyle() {
        assertThat(PrinceFormatRunner.intellijLanguageLevelToRelease(LanguageLevel.JDK_11)).isEqualTo(11);
        assertThat(PrinceFormatRunner.intellijLanguageLevelToRelease(LanguageLevel.JDK_17)).isEqualTo(17);
        assertThat(PrinceFormatRunner.intellijLanguageLevelToRelease(LanguageLevel.JDK_21)).isEqualTo(21);
    }
}
