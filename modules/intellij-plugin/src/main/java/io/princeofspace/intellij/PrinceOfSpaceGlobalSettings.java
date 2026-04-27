package io.princeofspace.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.princeofspace.intellij.PrinceOfSpaceState.CommonState;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;

/** Application-wide Prince of Space formatter defaults shared across projects. */
@State(
    name = "PrinceOfSpaceGlobalSettings",
    storages = @Storage("prince-of-space.xml")
)
public final class PrinceOfSpaceGlobalSettings
        implements PersistentStateComponent<CommonState> {

    private CommonState commonState = new CommonState();

    public static PrinceOfSpaceGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(PrinceOfSpaceGlobalSettings.class);
    }

    public FormatterConfig toFormatterConfig() {
        IndentStyle indentStyle = IndentStyle.valueOf(commonState.indentStyle);
        WrapStyle wrapStyle = WrapStyle.valueOf(commonState.wrapStyle);
        return FormatterConfig.builder()
                .indentStyle(indentStyle)
                .indentSize(commonState.indentSize)
                .lineLength(commonState.lineLength)
                .wrapStyle(wrapStyle)
                .closingParenOnNewLine(commonState.closingParenOnNewLine)
                .trailingCommas(commonState.trailingCommas)
                .javaLanguageLevel(JavaLanguageLevel.of(commonState.javaRelease))
                .build();
    }

    @Override
    public CommonState getState() {
        return commonState;
    }

    @Override
    public void loadState(CommonState loaded) {
        XmlSerializerUtil.copyBean(loaded, commonState);
        commonState.normalizeAfterLoad();
    }

    public void replaceState(CommonState newState) {
        XmlSerializerUtil.copyBean(newState, commonState);
        commonState.normalizeAfterLoad();
    }
}
