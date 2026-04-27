package io.princeofspace.intellij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import io.princeofspace.Formatter;
import io.princeofspace.FormatterException;
import io.princeofspace.model.FormatterConfig;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** Shared formatting logic for the manual action and format-on-save. */
public final class PrinceFormatRunner {

    private static final Logger LOG = Logger.getInstance(PrinceFormatRunner.class);

    private PrinceFormatRunner() {}

    /**
     * Formats the given Java file content in {@code document}. When {@code showErrorDialog} is true,
     * parse/format failures show a modal; otherwise they are logged (used during save).
     */
    public static void format(
            Project project,
            PsiJavaFile javaFile,
            Document document,
            boolean showErrorDialog) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        String text = document.getText();
        FormatterConfig config = PrinceOfSpaceProjectSettings.getInstance(project).toFormatterConfig(javaFile);
        Formatter formatter = new Formatter(config);
        VirtualFile vf = javaFile.getVirtualFile();
        Path nioPath = vf != null ? javaNioPath(vf) : null;
        try {
            String formatted = nioPath != null ? formatter.format(text, nioPath) : formatter.format(text);
            if (formatted.equals(text)) {
                return;
            }
            WriteCommandAction.runWriteCommandAction(
                    project,
                    "Prince of Space Format",
                    "PrinceOfSpace",
                    () -> {
                        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
                        document.setText(formatted);
                    });
        } catch (FormatterException ex) {
            if (showErrorDialog) {
                Messages.showErrorDialog(project, ex.getMessage(), "Prince of Space");
            } else {
                LOG.warn("Prince of Space format on save failed: " + ex.getMessage());
            }
        }
    }

    static @Nullable Path javaNioPath(VirtualFile vf) {
        try {
            if (vf.getFileSystem().getProtocol().equals("file")) {
                return vf.toNioPath();
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
            // non-local or unsupported VFS
        }
        return null;
    }
}
