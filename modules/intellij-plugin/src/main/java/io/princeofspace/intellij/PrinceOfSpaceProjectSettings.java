package io.princeofspace.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Per-project Prince of Space options (workspace file). Mirrors {@link FormatterConfig} knobs plus
 * {@link #formatOnSave} and language-level source selection.
 */
@State(
        name = "PrinceOfSpaceProjectSettings",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class PrinceOfSpaceProjectSettings implements PersistentStateComponent<PrinceOfSpaceProjectSettings.State> {

    private State state = new State();

    public static PrinceOfSpaceProjectSettings getInstance(@NotNull Project project) {
        return project.getService(PrinceOfSpaceProjectSettings.class);
    }

    /** Builds a {@link FormatterConfig} from saved options and the given Java file (for language level). */
    public @NotNull FormatterConfig toFormatterConfig(@NotNull PsiJavaFile javaFile) {
        State s = state;
        if (s.useGlobalFormatterSettings) {
            return PrinceOfSpaceGlobalSettings.getInstance().toFormatterConfig();
        }
        IndentStyle indentStyle = IndentStyle.valueOf(s.indentStyle);
        WrapStyle wrapStyle = WrapStyle.valueOf(s.wrapStyle);
        int release = s.useProjectLanguageLevel
                ? PrinceFormatRunner.intellijLanguageLevelToRelease(PsiUtil.getLanguageLevel(javaFile))
                : s.javaRelease;
        return FormatterConfig.builder()
                .indentStyle(indentStyle)
                .indentSize(s.indentSize)
                .lineLength(s.lineLength)
                .continuationIndentSize(s.continuationIndentSize)
                .wrapStyle(wrapStyle)
                .closingParenOnNewLine(s.closingParenOnNewLine)
                .trailingCommas(s.trailingCommas)
                .javaLanguageLevel(JavaLanguageLevel.of(release))
                .build();
    }

    public boolean isFormatOnSave() {
        return state.formatOnSave;
    }

    public void setFormatOnSave(boolean formatOnSave) {
        state.formatOnSave = formatOnSave;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        XmlSerializerUtil.copyBean(loaded, state);
        state.normalizeAfterLoad();
    }

    /** Replaces persisted state (e.g. from the settings UI) after validation. */
    public void replaceState(@NotNull State newState) {
        XmlSerializerUtil.copyBean(newState, state);
        state.normalizeAfterLoad();
    }

    public static final class State {
        /** Default on so new installs get save-time formatting without extra steps. */
        public boolean formatOnSave = true;
        /** If true, use IDE-global formatter settings instead of this project's formatter settings. */
        public boolean useGlobalFormatterSettings = false;

        public @NotNull String indentStyle = IndentStyle.SPACES.name();
        public int indentSize = 4;
        public int lineLength = 120;
        public int continuationIndentSize = 4;
        public @NotNull String wrapStyle = WrapStyle.BALANCED.name();
        public boolean closingParenOnNewLine = true;
        public boolean trailingCommas = false;

        /**
         * When {@code true}, JavaParser's language level follows the IDE language level for the file. When
         * {@code false}, {@link #javaRelease} is used.
         */
        public boolean useProjectLanguageLevel = true;

        /** Feature-release number (e.g. 17, 21) when {@link #useProjectLanguageLevel} is false. */
        public int javaRelease = 17;

        void normalizeAfterLoad() {
            if (indentStyle == null || indentStyle.isBlank()) {
                indentStyle = IndentStyle.SPACES.name();
            }
            if (wrapStyle == null || wrapStyle.isBlank()) {
                wrapStyle = WrapStyle.BALANCED.name();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            State state = (State) o;
            return formatOnSave == state.formatOnSave
                    && useGlobalFormatterSettings == state.useGlobalFormatterSettings
                    && indentSize == state.indentSize
                    && lineLength == state.lineLength
                    && continuationIndentSize == state.continuationIndentSize
                    && closingParenOnNewLine == state.closingParenOnNewLine
                    && trailingCommas == state.trailingCommas
                    && useProjectLanguageLevel == state.useProjectLanguageLevel
                    && javaRelease == state.javaRelease
                    && Objects.equals(indentStyle, state.indentStyle)
                    && Objects.equals(wrapStyle, state.wrapStyle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    formatOnSave,
                    useGlobalFormatterSettings,
                    indentStyle,
                    indentSize,
                    lineLength,
                    continuationIndentSize,
                    wrapStyle,
                    closingParenOnNewLine,
                    trailingCommas,
                    useProjectLanguageLevel,
                    javaRelease);
        }
    }
}
