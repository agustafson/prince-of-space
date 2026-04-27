package io.princeofspace.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import io.princeofspace.intellij.PrinceOfSpaceState.CommonState;
import io.princeofspace.intellij.PrinceOfSpaceState.ProjectState;
import io.princeofspace.internal.FormattingEngine;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;
import org.jetbrains.annotations.Nls;
import org.jspecify.annotations.NullUnmarked;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import static java.util.Objects.requireNonNull;

/**
 * Settings: <strong>Settings → Tools → Prince of Space</strong> — all {@link io.princeofspace.model.FormatterConfig}
 * knobs plus format-on-save and Java language level source.
 */
@NullUnmarked
public final class PrinceOfSpaceConfigurable implements Configurable {

    public static final int JAVA_LEVEL_DEFAULT = 17;
    public static final int JAVA_LEVEL_MIN = 1;
    public static final int JAVA_LEVEL_MAX = 25;
    private final PrinceOfSpaceProjectSettings settings;
    private final PrinceOfSpaceGlobalSettings globalSettings;

    private JBCheckBox formatOnSave;
    private JBCheckBox useGlobalFormatterSettings;
    private JBCheckBox useProjectLanguageLevel;
    private ComboBox<Integer> javaReleaseCombo;

    private ComboBox<String> indentStyleCombo;
    private JSpinner indentSizeSpinner;
    private JSpinner lineLengthSpinner;
    private ComboBox<String> wrapStyleCombo;
    private JBCheckBox closingParenOnNewLine;
    private JBCheckBox trailingCommas;

    private ProjectState baselineProject = new ProjectState();
    private CommonState baselineGlobal = new CommonState();

    public PrinceOfSpaceConfigurable(Project project) {
        this.settings = PrinceOfSpaceProjectSettings.getInstance(project);
        this.globalSettings = PrinceOfSpaceGlobalSettings.getInstance();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Prince of Space";
    }

    @Override
    @SuppressWarnings("MagicNumber")
    public JComponent createComponent() {
        formatOnSave = new JBCheckBox("Run Prince of Space formatter when saving Java files");
        useGlobalFormatterSettings =
                new JBCheckBox("Use IDE-global formatter settings (shared across projects)");
        useProjectLanguageLevel =
                new JBCheckBox("Use project / module Java language level for parsing (recommended)");
        javaReleaseCombo = new ComboBox<>(IntStream.rangeClosed(1, JAVA_LEVEL_MAX).boxed().toArray(Integer[]::new));

        indentStyleCombo = new ComboBox<>(Arrays.stream(IndentStyle.values()).map(Enum::name).toArray(String[]::new));
        indentSizeSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
        lineLengthSpinner = new JSpinner(new SpinnerNumberModel(120, 20, 500, 1));
        wrapStyleCombo = new ComboBox<>(Arrays.stream(WrapStyle.values()).map(Enum::name).toArray(String[]::new));
        closingParenOnNewLine = new JBCheckBox("Place closing \")\" on its own line when argument lists wrap");
        trailingCommas = new JBCheckBox("Use trailing commas in multi-line enums and array literals");

        useProjectLanguageLevel.addActionListener(e -> syncLanguageControls());
        useGlobalFormatterSettings.addActionListener(e -> syncLanguageControls());

        JPanel form =
                FormBuilder.createFormBuilder()
                        .addComponent(boldSection("On save"))
                        .addComponent(formatOnSave)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Scope"))
                        .addComponent(useGlobalFormatterSettings)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Java language level (JavaParser)"))
                        .addComponent(useProjectLanguageLevel)
                        .addLabeledComponent("Fixed language level (when not using project level):", javaReleaseCombo)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Indentation"))
                        .addLabeledComponent("Indent style:", indentStyleCombo)
                        .addLabeledComponent("Indent size (units per block level):", indentSizeSpinner)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Line width"))
                        .addLabeledComponent("Line length:", lineLengthSpinner)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Wrapping"))
                        .addLabeledComponent("Wrap style:", wrapStyleCombo)
                        .addComponent(closingParenOnNewLine)
                        .addComponent(trailingCommas)
                        .addComponentFillVertically(new JPanel(), 0)
                        .getPanel();

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(0, 0, 8, 0));
        root.add(settingsHintLabel(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(form);
        scroll.setPreferredSize(new Dimension(480, 420));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        JButton restore =
                new JButton("Restore formatting defaults");
        restore.addActionListener(
                e -> {
                    if (useGlobalFormatterSettings.isSelected()) {
                        loadUiFromState(settings.getState(), new CommonState());
                    } else {
                        loadUiFromState(new ProjectState(), globalSettings.getState());
                    }
                });
        root.add(restoreButtonPanel(restore), BorderLayout.SOUTH);

        loadUiFromState(settings.getState(), globalSettings.getState());
        baselineProject = copyProjectState(settings.getState());
        baselineGlobal = copyGlobalState(globalSettings.getState());
        return root;
    }

    private static JBLabel settingsHintLabel() {
        JBLabel hint =
                new JBLabel(
                        "<html><body style='width:420px;'>"
                                + "These options match the formatter's public configuration<br/>"
                                + "using a single <b>line length</b> threshold for wrapping.<br/><br/>"
                                + "Indent sizes are measured in the selected indent style units<br/>"
                                + "(spaces when using spaces, tabs when using tabs).<br/><br/>"
                                + "(see project documentation).<br/><br/>"
                                + "Manual <b>Code → Reformat with Prince of Space…</b> uses the same "
                                + "settings."
                                + "</body></html>");
        hint.setCopyable(true);
        return hint;
    }

    private static JPanel restoreButtonPanel(JButton restore) {
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(restore);
        return south;
    }

    private static JBLabel boldSection(String title) {
        JBLabel label = new JBLabel(title);
        Font base = label.getFont();
        label.setFont(base.deriveFont(base.getStyle() | Font.BOLD));
        return label;
    }

    private void syncLanguageControls() {
        if (useGlobalFormatterSettings.isSelected()) {
            useProjectLanguageLevel.setSelected(false);
            useProjectLanguageLevel.setEnabled(false);
            javaReleaseCombo.setEnabled(true);
            return;
        }
        useProjectLanguageLevel.setEnabled(true);
        boolean manual = !useProjectLanguageLevel.isSelected();
        javaReleaseCombo.setEnabled(manual);
    }

    private static ProjectState copyProjectState(ProjectState s) {
        ProjectState t = new ProjectState();
        t.commonState = copyGlobalState(s.commonState);
        t.formatOnSave = s.formatOnSave;
        t.useGlobalFormatterSettings = s.useGlobalFormatterSettings;
        t.useProjectLanguageLevel = s.useProjectLanguageLevel;
        return t;
    }

    private static CommonState copyGlobalState(CommonState s) {
        CommonState t = new CommonState();
        t.indentStyle = s.indentStyle;
        t.indentSize = s.indentSize;
        t.lineLength = s.lineLength;

        t.wrapStyle = s.wrapStyle;
        t.closingParenOnNewLine = s.closingParenOnNewLine;
        t.trailingCommas = s.trailingCommas;
        t.javaRelease = s.javaRelease;
        return t;
    }

    private void loadUiFromState(
        ProjectState projectState, CommonState globalState) {
        projectState.commonState.normalizeAfterLoad();
        globalState.normalizeAfterLoad();
        formatOnSave.setSelected(projectState.formatOnSave);
        useGlobalFormatterSettings.setSelected(projectState.useGlobalFormatterSettings);
        if (projectState.useGlobalFormatterSettings) {
            useProjectLanguageLevel.setSelected(false);
            updateState(globalState);
        } else {
            useProjectLanguageLevel.setSelected(projectState.useProjectLanguageLevel);
            updateState(projectState.commonState);
        }
        syncLanguageControls();
    }

    private void updateState(CommonState inputState) {
        indentStyleCombo.setSelectedItem(inputState.indentStyle);
        indentSizeSpinner.setValue(inputState.indentSize);

        lineLengthSpinner.setValue(inputState.lineLength);
        wrapStyleCombo.setSelectedItem(inputState.wrapStyle);
        closingParenOnNewLine.setSelected(inputState.closingParenOnNewLine);
        trailingCommas.setSelected(inputState.trailingCommas);
        javaReleaseCombo.setSelectedItem(inputState.javaRelease);
    }

    private ProjectState readUiProjectState() {
        ProjectState projectState = new ProjectState();
        projectState.formatOnSave = formatOnSave.isSelected();
        projectState.useGlobalFormatterSettings = useGlobalFormatterSettings.isSelected();
        projectState.useProjectLanguageLevel = useProjectLanguageLevel.isSelected();
        readUiState(projectState.commonState);
        return projectState;
    }

    private CommonState readUiGlobalState() {
        CommonState s = new CommonState();
        readUiState(s);
        return s;
    }

    private void readUiState(CommonState s) {
        s.indentStyle = requireNonNull((String) indentStyleCombo.getSelectedItem(), "indent style");
        s.indentSize = (Integer) indentSizeSpinner.getValue();

        s.lineLength = (Integer) lineLengthSpinner.getValue();
        s.wrapStyle = requireNonNull((String) wrapStyleCombo.getSelectedItem(), "wrap style");
        s.closingParenOnNewLine = closingParenOnNewLine.isSelected();
        s.trailingCommas = trailingCommas.isSelected();
        Object jr = javaReleaseCombo.getSelectedItem();
        s.javaRelease = jr instanceof Integer i ? i : JAVA_LEVEL_DEFAULT;
        s.normalizeAfterLoad();
    }

    private static void validateCommonState(CommonState commonState) throws ConfigurationException {
        try {
            IndentStyle.valueOf(commonState.indentStyle);
            WrapStyle.valueOf(commonState.wrapStyle);
            FormattingEngine.validateJavaReleaseForParser(commonState.javaRelease);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigurationException("Invalid global formatter setting: " + e.getMessage());
        }
        if (commonState.indentSize <= 0) {
            throw new ConfigurationException("Indent size must be positive.");
        }
        if (commonState.lineLength <= 0) {
            throw new ConfigurationException("Line length must be positive.");
        }
    }

    @Override
    public boolean isModified() {
        ProjectState uiProject = readUiProjectState();
        if (!uiProject.equals(baselineProject)) {
            return true;
        }
        if (uiProject.useGlobalFormatterSettings) {
            return !readUiGlobalState().equals(baselineGlobal);
        }
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        ProjectState projectState = readUiProjectState();
        if (projectState.useGlobalFormatterSettings) {
            CommonState globalState = readUiGlobalState();
            validateCommonState(globalState);
            settings.replaceState(projectState);
            globalSettings.replaceState(globalState);
        } else {
            validateCommonState(projectState.commonState);
            settings.replaceState(projectState);
        }
        baselineProject = copyProjectState(settings.getState());
        baselineGlobal = copyGlobalState(globalSettings.getState());
    }

    @Override
    public void reset() {
        loadUiFromState(settings.getState(), globalSettings.getState());
        baselineProject = copyProjectState(settings.getState());
        baselineGlobal = copyGlobalState(globalSettings.getState());
    }

    @Override
    public void disposeUIResources() {
        baselineProject = new ProjectState();
        baselineGlobal = new CommonState();
    }
}
