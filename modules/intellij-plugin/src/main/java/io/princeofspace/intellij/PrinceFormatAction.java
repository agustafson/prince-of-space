package io.princeofspace.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import io.princeofspace.Formatter;
import io.princeofspace.FormatterException;
import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaParserLanguageLevels;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** Reformats the active editor using {@link Formatter} and the file's IntelliJ language level. */
public final class PrinceFormatAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile f = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(f instanceof PsiJavaFile);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || !(psiFile instanceof PsiJavaFile javaFile)) {
            return;
        }
        Document document = editor.getDocument();
        String text = document.getText();
        int release = intellijLanguageLevelToRelease(PsiUtil.getLanguageLevel(javaFile));
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .javaLanguageLevel(JavaParserLanguageLevels.fromRelease(release))
                                .build());
        VirtualFile vf = javaFile.getVirtualFile();
        Path nioPath = vf != null ? javaNioPath(vf) : null;
        try {
            String formatted = nioPath != null ? formatter.format(text, nioPath) : formatter.format(text);
            WriteCommandAction.runWriteCommandAction(
                    project,
                    "Prince of Space Format",
                    "PrinceOfSpace",
                    () -> {
                        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
                        document.setText(formatted);
                    });
        } catch (FormatterException ex) {
            Messages.showErrorDialog(project, ex.getMessage(), "Prince of Space");
        }
    }

    private static int intellijLanguageLevelToRelease(@NotNull com.intellij.pom.java.LanguageLevel ll) {
        String n = ll.name();
        if (n.startsWith("JDK_1_")) {
            return Integer.parseInt(n.substring("JDK_1_".length()).replace("_", ""));
        }
        if (n.startsWith("JDK_")) {
            return Integer.parseInt(n.substring(4).replace("_", ""));
        }
        return 17;
    }

    private static Path javaNioPath(@NotNull VirtualFile vf) {
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
