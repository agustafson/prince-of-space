package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatterConfigTest {

    @Test
    void defaults_returnsExpectedValues() {
        FormatterConfig config = FormatterConfig.defaults();

        assertThat(config.indentStyle()).isEqualTo(IndentStyle.SPACES);
        assertThat(config.indentSize()).isEqualTo(4);
        assertThat(config.preferredLineLength()).isEqualTo(120);
        assertThat(config.maxLineLength()).isEqualTo(150);
        assertThat(config.continuationIndentSize()).isEqualTo(4);
        assertThat(config.wrapStyle()).isEqualTo(WrapStyle.BALANCED);
        assertThat(config.closingParenOnNewLine()).isTrue();
        assertThat(config.trailingCommas()).isFalse();
        assertThat(config.javaLanguageLevel()).isEqualTo(JavaLanguageLevel.of(17));
    }

    @Test
    void defaults_calledTwice_returnsEqualConfigs() {
        assertThat(FormatterConfig.defaults()).isEqualTo(FormatterConfig.defaults());
    }

    @Test
    void builder_overridesIndentStyleToTabs() {
        FormatterConfig config = FormatterConfig.builder()
                .indentStyle(IndentStyle.TABS)
                .build();

        assertThat(config.indentStyle()).isEqualTo(IndentStyle.TABS);
    }

    @Test
    void builder_overridesIndentSize() {
        FormatterConfig config = FormatterConfig.builder().indentSize(2).build();
        assertThat(config.indentSize()).isEqualTo(2);
    }

    @Test
    void builder_overridesLineLengths() {
        FormatterConfig config = FormatterConfig.builder()
                .preferredLineLength(80)
                .maxLineLength(100)
                .build();

        assertThat(config.preferredLineLength()).isEqualTo(80);
        assertThat(config.maxLineLength()).isEqualTo(100);
    }

    @Test
    void builder_overridesContinuationIndentSize() {
        FormatterConfig config = FormatterConfig.builder().continuationIndentSize(8).build();
        assertThat(config.continuationIndentSize()).isEqualTo(8);
    }

    @Test
    void builder_overridesWrapStyle() {
        assertThat(FormatterConfig.builder().wrapStyle(WrapStyle.WIDE).build().wrapStyle())
                .isEqualTo(WrapStyle.WIDE);
        assertThat(FormatterConfig.builder().wrapStyle(WrapStyle.NARROW).build().wrapStyle())
                .isEqualTo(WrapStyle.NARROW);
    }

    @Test
    void builder_overridesClosingParenOnNewLine() {
        FormatterConfig config = FormatterConfig.builder().closingParenOnNewLine(false).build();
        assertThat(config.closingParenOnNewLine()).isFalse();
    }

    @Test
    void builder_overridesTrailingCommas() {
        FormatterConfig config = FormatterConfig.builder().trailingCommas(true).build();
        assertThat(config.trailingCommas()).isTrue();
    }

    @Test
    void builder_overridesJavaLanguageLevel() {
        FormatterConfig config = FormatterConfig.builder()
                .javaLanguageLevel(JavaLanguageLevel.of(21))
                .build();
        assertThat(config.javaLanguageLevel()).isEqualTo(JavaLanguageLevel.of(21));
    }

    @Test
    void builder_previewLanguageLevel_roundtrips() {
        JavaLanguageLevel preview = JavaLanguageLevel.of(21, true);
        FormatterConfig config = FormatterConfig.builder()
                .javaLanguageLevel(preview)
                .build();
        assertThat(config.javaLanguageLevel().level()).isEqualTo(21);
        assertThat(config.javaLanguageLevel().preview()).isTrue();
    }

    @Test
    void validation_indentSizeZeroThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().indentSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indentSize");
    }

    @Test
    void validation_indentSizeNegativeThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().indentSize(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validation_continuationIndentSizeZeroThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().continuationIndentSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("continuationIndentSize");
    }

    @Test
    void validation_maxLessThanPreferredThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder()
                        .preferredLineLength(120)
                        .maxLineLength(100)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLineLength")
                .hasMessageContaining("preferredLineLength");
    }

    @Test
    void validation_maxEqualToPreferredIsAllowed() {
        FormatterConfig config = FormatterConfig.builder()
                .preferredLineLength(100)
                .maxLineLength(100)
                .build();
        assertThat(config.maxLineLength()).isEqualTo(100);
    }

    @Test
    void validation_preferredLineLengthZeroThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().preferredLineLength(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validation_maxLineLengthZeroThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().maxLineLength(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validation_nullIndentStyleThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().indentStyle(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validation_nullWrapStyleThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().wrapStyle(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validation_nullJavaLanguageLevelThrows() {
        assertThatThrownBy(() -> FormatterConfig.builder().javaLanguageLevel(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCode_sameValues_equal() {
        FormatterConfig a = FormatterConfig.builder().indentSize(2).build();
        FormatterConfig b = FormatterConfig.builder().indentSize(2).build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equalsAndHashCode_differentValues_notEqual() {
        FormatterConfig a = FormatterConfig.builder().indentSize(2).build();
        FormatterConfig b = FormatterConfig.builder().indentSize(4).build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toString_containsKeyFields() {
        // Records produce: FormatterConfig[field=value, ...]
        String str = FormatterConfig.defaults().toString();
        assertThat(str).contains("indentStyle=SPACES");
        assertThat(str).contains("indentSize=4");
        assertThat(str).contains("preferredLineLength=120");
        assertThat(str).contains("maxLineLength=150");
        assertThat(str).contains("wrapStyle=BALANCED");
    }
}
