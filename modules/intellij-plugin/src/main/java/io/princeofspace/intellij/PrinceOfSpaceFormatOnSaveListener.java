package io.princeofspace.intellij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;

/**
 * Runs the Prince of Space formatter before a document is written to disk when
 * {@link PrinceOfSpaceProjectSettings#isFormatOnSave()} is enabled for that project.
 */
public final class PrinceOfSpaceFormatOnSaveListener implements FileDocumentManagerListener {

    /** Avoid re-entry when {@link Document#setText} triggers another save pass. */
    private static final ThreadLocal<Boolean> FORMATTING = ThreadLocal.withInitial(() -> false);

    @Override
    public void beforeDocumentSaving(Document document) {
        if (Boolean.TRUE.equals(FORMATTING.get())) {
            return;
        }
        VirtualFile vf = FileDocumentManager.getInstance().getFile(document);
        if (vf == null) {
            return;
        }
        Project project = ProjectLocator.getInstance().guessProjectForFile(vf);
        if (project == null || project.isDisposed()) {
            return;
        }
        if (!PrinceOfSpaceProjectSettings.getInstance(project).isFormatOnSave()) {
            return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            return;
        }
        FORMATTING.set(Boolean.TRUE);
        try {
            // Commit PSI to match editor; then format (may replace document text).
            PsiDocumentManager.getInstance(project).commitDocument(document);
            PrinceFormatRunner.format(project, javaFile, document, false);
        } finally {
            FORMATTING.remove();
        }
    }
}
