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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the Prince of Space formatter before a document is written to disk when
 * {@link PrinceOfSpaceProjectSettings#isFormatOnSave()} is enabled for that project.
 */
public final class PrinceOfSpaceFormatOnSaveListener implements FileDocumentManagerListener {

    /**
     * Documents currently being formatted. Keyed by {@link Document} identity so the guard catches
     * both same-thread re-entry (when {@code setText} inside the formatter triggers another save
     * pass) and cross-thread races (modern IntelliJ may dispatch save listeners off the EDT, so
     * two threads can attempt to format the same document concurrently).
     */
    private static final Set<Document> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    @Override
    public void beforeDocumentSaving(Document document) {
        if (!IN_PROGRESS.add(document)) {
            return;
        }
        try {
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
            // Commit PSI to match editor; then format (may replace document text).
            PsiDocumentManager.getInstance(project).commitDocument(document);
            PrinceFormatRunner.format(project, javaFile, document, false);
        } finally {
            IN_PROGRESS.remove(document);
        }
    }
}
