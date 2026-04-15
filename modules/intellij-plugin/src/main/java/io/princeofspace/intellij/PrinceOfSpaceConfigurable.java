package io.princeofspace.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import io.princeofspace.model.IndentStyle;
import io.princeofspace.model.JavaParserLanguageLevels;
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

    private JBCheckBox formatOnSave;
    private JBCheckBox useProjectLanguageLevel;
    private ComboBox<Integer> javaReleaseCombo;

    private ComboBox<String> indentStyleCombo;
    private JSpinner indentSizeSpinner;
    private JSpinner continuationIndentSpinner;
    private JSpinner preferredLineLengthSpinner;
    private JSpinner maxLineLengthSpinner;
    private ComboBox<String> wrapStyleCombo;
    private JBCheckBox closingParenOnNewLine;
    private JBCheckBox trailingCommas;

    private PrinceOfSpaceProjectSettings.State baseline;

    public PrinceOfSpaceConfigurable(@NotNull Project project) {
        this.settings = PrinceOfSpaceProjectSettings.getInstance(project);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getDisplayName() {
        return "Prince of Space";
    }

    @Override
    public @Nullable JComponent createComponent() {
        formatOnSave = new JBCheckBox("Run Prince of Space formatter when saving Java files");
        useProjectLanguageLevel =
                new JBCheckBox("Use project / module Java language level for parsing (recommended)");
        javaReleaseCombo = new ComboBox<>(IntStream.rangeClosed(1, 25).boxed().toArray(Integer[]::new));

        indentStyleCombo = new ComboBox<>(Arrays.stream(IndentStyle.values()).map(Enum::name).toArray(String[]::new));
        indentSizeSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
        continuationIndentSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
        preferredLineLengthSpinner = new JSpinner(new SpinnerNumberModel(120, 20, 500, 1));
        maxLineLengthSpinner = new JSpinner(new SpinnerNumberModel(150, 20, 800, 1));
        wrapStyleCombo = new ComboBox<>(Arrays.stream(WrapStyle.values()).map(Enum::name).toArray(String[]::new));
        closingParenOnNewLine = new JBCheckBox("Place closing \")\" on its own line when argument lists wrap");
        trailingCommas = new JBCheckBox("Use trailing commas in multi-line enums and array literals");

        useProjectLanguageLevel.addActionListener(e -> syncLanguageControls());

        JPanel form =
                FormBuilder.createFormBuilder()
                        .addComponent(boldSection("On save"))
                        .addComponent(formatOnSave)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Java language level (JavaParser)"))
                        .addComponent(useProjectLanguageLevel)
                        .addLabeledComponent("Fixed language level (when not using project level):", javaReleaseCombo)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Indentation"))
                        .addLabeledComponent("Indent style:", indentStyleCombo)
                        .addLabeledComponent("Indent size:", indentSizeSpinner)
                        .addLabeledComponent("Continuation indent size:", continuationIndentSpinner)
                        .addVerticalGap(8)
                        .addComponent(boldSection("Line width"))
                        .addLabeledComponent("Preferred line length:", preferredLineLengthSpinner)
                        .addLabeledComponent("Max line length:", maxLineLengthSpinner)
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
                                + "These options match the formatter&apos;s public configuration (see project documentation). "
                                + "Manual <b>Code → Reformat with Prince of Space…</b> uses the same settings."
                                + "</body></html>");
        hint.setCopyable(true);
        root.add(hint, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(form);
        scroll.setPreferredSize(new Dimension(480, 420));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        JButton restore =
                new JButton("Restore formatting defaults");
        restore.addActionListener(e -> loadUiFromState(new PrinceOfSpaceProjectSettings.State()));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(restore);
        root.add(south, BorderLayout.SOUTH);

        loadUiFromState(settings.getState());
        baseline = copyState(settings.getState());
        return root;
    }

    private static @NotNull JBLabel boldSection(@NotNull String title) {
        JBLabel label = new JBLabel(title);
        Font base = label.getFont();
        label.setFont(base.deriveFont(base.getStyle() | Font.BOLD));
        return label;
    }

    private void syncLanguageControls() {
        boolean manual = !useProjectLanguageLevel.isSelected();
        javaReleaseCombo.setEnabled(manual);
    }

    private static PrinceOfSpaceProjectSettings.State copyState(PrinceOfSpaceProjectSettings.State s) {
        PrinceOfSpaceProjectSettings.State t = new PrinceOfSpaceProjectSettings.State();
        t.formatOnSave = s.formatOnSave;
        t.indentStyle = s.indentStyle;
        t.indentSize = s.indentSize;
        t.preferredLineLength = s.preferredLineLength;
        t.maxLineLength = s.maxLineLength;
        t.continuationIndentSize = s.continuationIndentSize;
        t.wrapStyle = s.wrapStyle;
        t.closingParenOnNewLine = s.closingParenOnNewLine;
        t.trailingCommas = s.trailingCommas;
        t.useProjectLanguageLevel = s.useProjectLanguageLevel;
        t.javaRelease = s.javaRelease;
        return t;
    }

    private void loadUiFromState(PrinceOfSpaceProjectSettings.State s) {
        s.normalizeAfterLoad();
        formatOnSave.setSelected(s.formatOnSave);
        useProjectLanguageLevel.setSelected(s.useProjectLanguageLevel);
        indentStyleCombo.setSelectedItem(s.indentStyle);
        indentSizeSpinner.setValue(s.indentSize);
        continuationIndentSpinner.setValue(s.continuationIndentSize);
        preferredLineLengthSpinner.setValue(s.preferredLineLength);
        maxLineLengthSpinner.setValue(s.maxLineLength);
        wrapStyleCombo.setSelectedItem(s.wrapStyle);
        closingParenOnNewLine.setSelected(s.closingParenOnNewLine);
        trailingCommas.setSelected(s.trailingCommas);
        javaReleaseCombo.setSelectedItem(s.javaRelease);
        syncLanguageControls();
    }

    private PrinceOfSpaceProjectSettings.State readUiState() {
        PrinceOfSpaceProjectSettings.State s = new PrinceOfSpaceProjectSettings.State();
        s.formatOnSave = formatOnSave.isSelected();
        s.useProjectLanguageLevel = useProjectLanguageLevel.isSelected();
        s.indentStyle = (String) indentStyleCombo.getSelectedItem();
        s.indentSize = (Integer) indentSizeSpinner.getValue();
        s.continuationIndentSize = (Integer) continuationIndentSpinner.getValue();
        s.preferredLineLength = (Integer) preferredLineLengthSpinner.getValue();
        s.maxLineLength = (Integer) maxLineLengthSpinner.getValue();
        s.wrapStyle = (String) wrapStyleCombo.getSelectedItem();
        s.closingParenOnNewLine = closingParenOnNewLine.isSelected();
        s.trailingCommas = trailingCommas.isSelected();
        Object jr = javaReleaseCombo.getSelectedItem();
        s.javaRelease = jr instanceof Integer ? (Integer) jr : 17;
        s.normalizeAfterLoad();
        return s;
    }

    private static void validateState(PrinceOfSpaceProjectSettings.State s) throws ConfigurationException {
        try {
            IndentStyle.valueOf(s.indentStyle);
            WrapStyle.valueOf(s.wrapStyle);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigurationException("Invalid indent or wrap style: " + e.getMessage());
        }
        if (s.indentSize <= 0 || s.continuationIndentSize <= 0) {
            throw new ConfigurationException("Indent sizes must be positive.");
        }
        if (s.preferredLineLength <= 0 || s.maxLineLength <= 0) {
            throw new ConfigurationException("Line lengths must be positive.");
        }
        if (s.maxLineLength < s.preferredLineLength) {
            throw new ConfigurationException("Max line length must be greater than or equal to preferred line length.");
        }
        if (!s.useProjectLanguageLevel) {
            try {
                JavaParserLanguageLevels.fromRelease(s.javaRelease);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(e.getMessage());
            }
        }
    }

    @Override
    public boolean isModified() {
        if (baseline == null) {
            return false;
        }
        return !readUiState().equals(baseline);
    }

    @Override
    public void apply() throws ConfigurationException {
        PrinceOfSpaceProjectSettings.State s = readUiState();
        validateState(s);
        settings.replaceState(s);
        baseline = copyState(settings.getState());
    }

    @Override
    public void reset() {
        loadUiFromState(settings.getState());
        baseline = copyState(settings.getState());
    }

    @Override
    public void disposeUIResources() {
        baseline = null;
    }
}
