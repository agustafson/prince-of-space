package io.princeofspace.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.princeofspace.intellij.PrinceOfSpaceState.ProjectState;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaLanguageLevel;
import io.princeofspace.model.WrapStyle;

/**
 * Per-project Prince of Space options stored under {@code .idea/prince-of-space.xml}, suitable to commit when teams
 * track {@code .idea/}. Mirrors {@link FormatterConfig} knobs plus {@link ProjectState#formatOnSave} and
 * language-level source selection.
 */
@State(
        name = "PrinceOfSpaceProjectSettings",
        storages = @Storage("prince-of-space.xml")
)
public final class PrinceOfSpaceProjectSettings implements PersistentStateComponent<ProjectState> {

    private final Object lock = new Object();
    private ProjectState projectState = new ProjectState();

    public static PrinceOfSpaceProjectSettings getInstance(Project project) {
        return project.getService(PrinceOfSpaceProjectSettings.class);
    }

    /** Builds a {@link FormatterConfig} from saved options and the given Java file (for language level). */
    public FormatterConfig toFormatterConfig(PsiJavaFile javaFile) {
        ProjectState snapshot;
        synchronized (lock) {
            snapshot = projectState.copy();
        }
        if (snapshot.useGlobalFormatterSettings) {
            return PrinceOfSpaceGlobalSettings.getInstance().toFormatterConfig();
        }
        PrinceOfSpaceState.CommonState state = snapshot.commonState;
        IndentStyle indentStyle = IndentStyle.valueOf(state.indentStyle);
        WrapStyle wrapStyle = WrapStyle.valueOf(state.wrapStyle);
        int release = snapshot.useProjectLanguageLevel
            ? PsiUtil.getLanguageLevel(javaFile).toJavaVersion().feature
            : state.javaRelease;
        return FormatterConfig.builder()
                .indentStyle(indentStyle)
                .indentSize(state.indentSize)
                .lineLength(state.lineLength)
                .wrapStyle(wrapStyle)
                .closingParenOnNewLine(state.closingParenOnNewLine)
                .trailingCommas(state.trailingCommas)
                .javaLanguageLevel(JavaLanguageLevel.of(release))
                .build();
    }

    public boolean isFormatOnSave() {
        synchronized (lock) {
            return projectState.formatOnSave;
        }
    }

    public void setFormatOnSave(boolean formatOnSave) {
        synchronized (lock) {
            projectState.formatOnSave = formatOnSave;
        }
    }

    @Override
    public ProjectState getState() {
        synchronized (lock) {
            return projectState.copy();
        }
    }

    @Override
    public void loadState(ProjectState loaded) {
        synchronized (lock) {
            XmlSerializerUtil.copyBean(loaded, projectState);
            projectState.commonState.normalizeAfterLoad();
        }
    }

    /** Replaces persisted state (e.g. from the settings UI) after validation. */
    public void replaceState(ProjectState newProjectState) {
        synchronized (lock) {
            XmlSerializerUtil.copyBean(newProjectState, projectState);
            projectState.commonState.normalizeAfterLoad();
        }
    }

}
