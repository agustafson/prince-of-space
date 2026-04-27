package io.princeofspace.intellij;

import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;

import java.util.Objects;

import static io.princeofspace.intellij.PrinceOfSpaceConfigurable.JAVA_LEVEL_DEFAULT;
import static io.princeofspace.intellij.PrinceOfSpaceConfigurable.JAVA_LEVEL_MAX;
import static io.princeofspace.intellij.PrinceOfSpaceConfigurable.JAVA_LEVEL_MIN;

interface PrinceOfSpaceState {

    final class CommonState {
        private static final int LINE_LENGTH_MIN = 20;
        private static final int LINE_LENGTH_MAX = 500;
        private static final int LINE_LENGTH_DEFAULT = 120;
        private static final int INDENT_SIZE_MIN = 1;
        private static final int INDENT_SIZE_MAX = 32;
        private static final int INDENT_SIZE_DEFAULT = 4;

        public String indentStyle = IndentStyle.SPACES.name();
        public int indentSize = INDENT_SIZE_DEFAULT;
        public int lineLength = LINE_LENGTH_DEFAULT;
        public String wrapStyle = WrapStyle.BALANCED.name();
        public boolean closingParenOnNewLine = true;
        public boolean trailingCommas = false;
        public int javaRelease = JAVA_LEVEL_DEFAULT;

        void normalizeAfterLoad() {
            if (indentStyle == null || indentStyle.isBlank()) {
                indentStyle = IndentStyle.SPACES.name();
            }
            if (wrapStyle == null || wrapStyle.isBlank()) {
                wrapStyle = WrapStyle.BALANCED.name();
            }
            indentSize = clamp(indentSize, INDENT_SIZE_MIN, INDENT_SIZE_MAX, INDENT_SIZE_DEFAULT);
            lineLength = clamp(lineLength, LINE_LENGTH_MIN, LINE_LENGTH_MAX, LINE_LENGTH_DEFAULT);
            javaRelease = clamp(javaRelease, JAVA_LEVEL_MIN, JAVA_LEVEL_MAX, JAVA_LEVEL_DEFAULT);
        }

        private static int clamp(int value, int min, int max, int fallback) {
            if (value <= 0) {
                return fallback;
            }
            return Math.min(max, Math.max(min, value));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CommonState state = (CommonState) o;
            return indentSize == state.indentSize
                && lineLength == state.lineLength
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
                wrapStyle,
                closingParenOnNewLine,
                trailingCommas,
                javaRelease);
        }
    }

    final class ProjectState {
        public CommonState commonState = new CommonState();
        /**
         * Default on so new installs get save-time formatting without extra steps.
         */
        public boolean formatOnSave = true;
        /**
         * If true, use IDE-global formatter settings instead of this project's formatter settings.
         */
        public boolean useGlobalFormatterSettings = false;

        /**
         * When {@code true}, JavaParser's language level follows the IDE language level for the file. When
         * {@code false}, {@link #commonState#javaRelease} is used.
         */
        public boolean useProjectLanguageLevel = true;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectState projectState = (ProjectState) o;
            return Objects.equals(commonState, projectState.commonState)
                && formatOnSave == projectState.formatOnSave
                && useGlobalFormatterSettings == projectState.useGlobalFormatterSettings
                && useProjectLanguageLevel == projectState.useProjectLanguageLevel;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                commonState,
                formatOnSave,
                useGlobalFormatterSettings,
                useProjectLanguageLevel);
        }
    }
}
