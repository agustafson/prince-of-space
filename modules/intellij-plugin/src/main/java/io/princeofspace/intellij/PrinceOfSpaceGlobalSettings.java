package io.princeofspace.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Application-wide Prince of Space formatter defaults shared across projects. */
@State(name = "PrinceOfSpaceGlobalSettings", storages = @Storage("prince-of-space.xml"))
public final class PrinceOfSpaceGlobalSettings
        implements PersistentStateComponent<PrinceOfSpaceGlobalSettings.State> {

    private State state = new State();

    public static @NotNull PrinceOfSpaceGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(PrinceOfSpaceGlobalSettings.class);
    }

    public @NotNull FormatterConfig toFormatterConfig() {
        State s = state;
        IndentStyle indentStyle = IndentStyle.valueOf(s.indentStyle);
        WrapStyle wrapStyle = WrapStyle.valueOf(s.wrapStyle);
        return FormatterConfig.builder()
                .indentStyle(indentStyle)
                .indentSize(s.indentSize)
                .lineLength(s.lineLength)
                .continuationIndentSize(s.continuationIndentSize)
                .wrapStyle(wrapStyle)
                .closingParenOnNewLine(s.closingParenOnNewLine)
                .trailingCommas(s.trailingCommas)
                .javaLanguageLevel(JavaLanguageLevel.of(s.javaRelease))
                .build();
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

    public void replaceState(@NotNull State newState) {
        XmlSerializerUtil.copyBean(newState, state);
        state.normalizeAfterLoad();
    }

    public static final class State {
        public @NotNull String indentStyle = IndentStyle.SPACES.name();
        public int indentSize = 4;
        public int lineLength = 120;
        public int continuationIndentSize = 4;
        public @NotNull String wrapStyle = WrapStyle.BALANCED.name();
        public boolean closingParenOnNewLine = true;
        public boolean trailingCommas = false;
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
            return indentSize == state.indentSize
                    && lineLength == state.lineLength
                    && continuationIndentSize == state.continuationIndentSize
                    && closingParenOnNewLine == state.closingParenOnNewLine
                    && trailingCommas == state.trailingCommas
                    && javaRelease == state.javaRelease
                    && Objects.equals(indentStyle, state.indentStyle)
                    && Objects.equals(wrapStyle, state.wrapStyle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    indentStyle,
                    indentSize,
                    lineLength,
                    continuationIndentSize,
                    wrapStyle,
                    closingParenOnNewLine,
                    trailingCommas,
                    javaRelease);
        }
    }
}
