package io.princeofspace.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import io.princeofspace.internal.FormattingEngine;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.WrapStyle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.stream.IntStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Settings: <strong>Settings → Tools → Prince of Space</strong> — all {@link io.princeofspace.model.FormatterConfig}
 * knobs plus format-on-save and Java language level source.
 */
public final class PrinceOfSpaceConfigurable implements Configurable {

    private final PrinceOfSpaceProjectSettings settings;
    private final PrinceOfSpaceGlobalSettings globalSettings;

    private JBCheckBox formatOnSave;
    private JBCheckBox useGlobalFormatterSettings;
    private JBCheckBox useProjectLanguageLevel;
    private ComboBox<Integer> javaReleaseCombo;

    private ComboBox<String> indentStyleCombo;
    private JSpinner indentSizeSpinner;
    private JSpinner continuationIndentSpinner;
    private JSpinner lineLengthSpinner;
    private ComboBox<String> wrapStyleCombo;
    private JBCheckBox closingParenOnNewLine;
    private JBCheckBox trailingCommas;

    private PrinceOfSpaceProjectSettings.State baselineProject;
    private PrinceOfSpaceGlobalSettings.State baselineGlobal;

    public PrinceOfSpaceConfigurable(@NotNull Project project) {
        this.settings = PrinceOfSpaceProjectSettings.getInstance(project);
        this.globalSettings = PrinceOfSpaceGlobalSettings.getInstance();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getDisplayName() {
        return "Prince of Space";
    }

    @Override
    public @Nullable JComponent createComponent() {
        formatOnSave = new JBCheckBox("Run Prince of Space formatter when saving Java files");
        useGlobalFormatterSettings =
                new JBCheckBox("Use IDE-global formatter settings (shared across projects)");
        useProjectLanguageLevel =
                new JBCheckBox("Use project / module Java language level for parsing (recommended)");
        javaReleaseCombo = new ComboBox<>(IntStream.rangeClosed(1, 25).boxed().toArray(Integer[]::new));

        indentStyleCombo = new ComboBox<>(Arrays.stream(IndentStyle.values()).map(Enum::name).toArray(String[]::new));
        indentSizeSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
        continuationIndentSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
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
                        .addLabeledComponent("Continuation indent size (units on wrapped lines):", continuationIndentSpinner)
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
        root.add(hint, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(form);
        scroll.setPreferredSize(new Dimension(480, 420));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        JButton restore =
                new JButton("Restore formatting defaults");
        restore.addActionListener(
                e -> {
                    if (useGlobalFormatterSettings.isSelected()) {
                        loadUiFromState(settings.getState(), new PrinceOfSpaceGlobalSettings.State());
                    } else {
                        loadUiFromState(new PrinceOfSpaceProjectSettings.State(), globalSettings.getState());
                    }
                });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(restore);
        root.add(south, BorderLayout.SOUTH);

        loadUiFromState(settings.getState(), globalSettings.getState());
        baselineProject = copyProjectState(settings.getState());
        baselineGlobal = copyGlobalState(globalSettings.getState());
        return root;
    }

    private static @NotNull JBLabel boldSection(@NotNull String title) {
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

    private static PrinceOfSpaceProjectSettings.State copyProjectState(PrinceOfSpaceProjectSettings.State s) {
        PrinceOfSpaceProjectSettings.State t = new PrinceOfSpaceProjectSettings.State();
        t.formatOnSave = s.formatOnSave;
        t.useGlobalFormatterSettings = s.useGlobalFormatterSettings;
        t.indentStyle = s.indentStyle;
        t.indentSize = s.indentSize;
        t.lineLength = s.lineLength;
        t.continuationIndentSize = s.continuationIndentSize;
        t.wrapStyle = s.wrapStyle;
        t.closingParenOnNewLine = s.closingParenOnNewLine;
        t.trailingCommas = s.trailingCommas;
        t.useProjectLanguageLevel = s.useProjectLanguageLevel;
        t.javaRelease = s.javaRelease;
        return t;
    }

    private static PrinceOfSpaceGlobalSettings.State copyGlobalState(PrinceOfSpaceGlobalSettings.State s) {
        PrinceOfSpaceGlobalSettings.State t = new PrinceOfSpaceGlobalSettings.State();
        t.indentStyle = s.indentStyle;
        t.indentSize = s.indentSize;
        t.lineLength = s.lineLength;
        t.continuationIndentSize = s.continuationIndentSize;
        t.wrapStyle = s.wrapStyle;
        t.closingParenOnNewLine = s.closingParenOnNewLine;
        t.trailingCommas = s.trailingCommas;
        t.javaRelease = s.javaRelease;
        return t;
    }

    private void loadUiFromState(
            PrinceOfSpaceProjectSettings.State projectState, PrinceOfSpaceGlobalSettings.State globalState) {
        projectState.normalizeAfterLoad();
        globalState.normalizeAfterLoad();
        formatOnSave.setSelected(projectState.formatOnSave);
        useGlobalFormatterSettings.setSelected(projectState.useGlobalFormatterSettings);
        if (projectState.useGlobalFormatterSettings) {
            useProjectLanguageLevel.setSelected(false);
            indentStyleCombo.setSelectedItem(globalState.indentStyle);
            indentSizeSpinner.setValue(globalState.indentSize);
            continuationIndentSpinner.setValue(globalState.continuationIndentSize);
            lineLengthSpinner.setValue(globalState.lineLength);
            wrapStyleCombo.setSelectedItem(globalState.wrapStyle);
            closingParenOnNewLine.setSelected(globalState.closingParenOnNewLine);
            trailingCommas.setSelected(globalState.trailingCommas);
            javaReleaseCombo.setSelectedItem(globalState.javaRelease);
        } else {
            useProjectLanguageLevel.setSelected(projectState.useProjectLanguageLevel);
            indentStyleCombo.setSelectedItem(projectState.indentStyle);
            indentSizeSpinner.setValue(projectState.indentSize);
            continuationIndentSpinner.setValue(projectState.continuationIndentSize);
            lineLengthSpinner.setValue(projectState.lineLength);
            wrapStyleCombo.setSelectedItem(projectState.wrapStyle);
            closingParenOnNewLine.setSelected(projectState.closingParenOnNewLine);
            trailingCommas.setSelected(projectState.trailingCommas);
            javaReleaseCombo.setSelectedItem(projectState.javaRelease);
        }
        syncLanguageControls();
    }

    private PrinceOfSpaceProjectSettings.State readUiProjectState() {
        PrinceOfSpaceProjectSettings.State s = new PrinceOfSpaceProjectSettings.State();
        s.formatOnSave = formatOnSave.isSelected();
        s.useGlobalFormatterSettings = useGlobalFormatterSettings.isSelected();
        s.useProjectLanguageLevel = useProjectLanguageLevel.isSelected();
        s.indentStyle = (String) indentStyleCombo.getSelectedItem();
        s.indentSize = (Integer) indentSizeSpinner.getValue();
        s.continuationIndentSize = (Integer) continuationIndentSpinner.getValue();
        s.lineLength = (Integer) lineLengthSpinner.getValue();
        s.wrapStyle = (String) wrapStyleCombo.getSelectedItem();
        s.closingParenOnNewLine = closingParenOnNewLine.isSelected();
        s.trailingCommas = trailingCommas.isSelected();
        Object jr = javaReleaseCombo.getSelectedItem();
        s.javaRelease = jr instanceof Integer ? (Integer) jr : 17;
        s.normalizeAfterLoad();
        return s;
    }

    private PrinceOfSpaceGlobalSettings.State readUiGlobalState() {
        PrinceOfSpaceGlobalSettings.State s = new PrinceOfSpaceGlobalSettings.State();
        s.indentStyle = (String) indentStyleCombo.getSelectedItem();
        s.indentSize = (Integer) indentSizeSpinner.getValue();
        s.continuationIndentSize = (Integer) continuationIndentSpinner.getValue();
        s.lineLength = (Integer) lineLengthSpinner.getValue();
        s.wrapStyle = (String) wrapStyleCombo.getSelectedItem();
        s.closingParenOnNewLine = closingParenOnNewLine.isSelected();
        s.trailingCommas = trailingCommas.isSelected();
        Object jr = javaReleaseCombo.getSelectedItem();
        s.javaRelease = jr instanceof Integer ? (Integer) jr : 17;
        s.normalizeAfterLoad();
        return s;
    }

    private static void validateProjectState(PrinceOfSpaceProjectSettings.State s) throws ConfigurationException {
        try {
            IndentStyle.valueOf(s.indentStyle);
            WrapStyle.valueOf(s.wrapStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigurationException("Invalid indent or wrap style: " + e.getMessage());
        }
        if (s.indentSize <= 0 || s.continuationIndentSize <= 0) {
            throw new ConfigurationException("Indent sizes must be positive.");
        }
        if (s.lineLength <= 0) {
            throw new ConfigurationException("Line length must be positive.");
        }
        if (!s.useProjectLanguageLevel) {
            try {
                FormattingEngine.validateJavaReleaseForParser(s.javaRelease);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(e.getMessage());
            }
        }
    }

    private static void validateGlobalState(PrinceOfSpaceGlobalSettings.State s) throws ConfigurationException {
        try {
            IndentStyle.valueOf(s.indentStyle);
            WrapStyle.valueOf(s.wrapStyle);
            FormattingEngine.validateJavaReleaseForParser(s.javaRelease);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigurationException("Invalid global formatter setting: " + e.getMessage());
        }
        if (s.indentSize <= 0 || s.continuationIndentSize <= 0) {
            throw new ConfigurationException("Indent sizes must be positive.");
        }
        if (s.lineLength <= 0) {
            throw new ConfigurationException("Line length must be positive.");
        }
    }

    @Override
    public boolean isModified() {
        if (baselineProject == null || baselineGlobal == null) {
            return false;
        }
        PrinceOfSpaceProjectSettings.State uiProject = readUiProjectState();
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
        PrinceOfSpaceProjectSettings.State projectState = readUiProjectState();
        if (projectState.useGlobalFormatterSettings) {
            PrinceOfSpaceGlobalSettings.State globalState = readUiGlobalState();
            validateGlobalState(globalState);
            settings.replaceState(projectState);
            globalSettings.replaceState(globalState);
        } else {
            validateProjectState(projectState);
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
        baselineProject = null;
        baselineGlobal = null;
    }
}
