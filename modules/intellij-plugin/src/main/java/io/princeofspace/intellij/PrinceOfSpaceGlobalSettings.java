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

    private final Object lock = new Object();
    private CommonState commonState = new CommonState();

    public static PrinceOfSpaceGlobalSettings getInstance() {
        return ApplicationManager.getApplication().getService(PrinceOfSpaceGlobalSettings.class);
    }

    public FormatterConfig toFormatterConfig() {
        CommonState snapshot;
        synchronized (lock) {
            snapshot = commonState.copy();
        }
        IndentStyle indentStyle = IndentStyle.valueOf(snapshot.indentStyle);
        WrapStyle wrapStyle = WrapStyle.valueOf(snapshot.wrapStyle);
        return FormatterConfig.builder()
                .indentStyle(indentStyle)
                .indentSize(snapshot.indentSize)
                .lineLength(snapshot.lineLength)
                .wrapStyle(wrapStyle)
                .closingParenOnNewLine(snapshot.closingParenOnNewLine)
                .trailingCommas(snapshot.trailingCommas)
                .javaLanguageLevel(JavaLanguageLevel.of(snapshot.javaRelease))
                .build();
    }

    @Override
    public CommonState getState() {
        synchronized (lock) {
            return commonState.copy();
        }
    }

    @Override
    public void loadState(CommonState loaded) {
        synchronized (lock) {
            XmlSerializerUtil.copyBean(loaded, commonState);
            commonState.normalizeAfterLoad();
        }
    }

    public void replaceState(CommonState newState) {
        synchronized (lock) {
            XmlSerializerUtil.copyBean(newState, commonState);
            commonState.normalizeAfterLoad();
        }
    }
}
