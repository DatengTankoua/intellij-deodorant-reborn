package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Repräsentiert ein duplizierten Code-Fragment.
 * 
 * Enthält Informationen über:
 * - Die Datei, in der das Duplikat gefunden wurde
 * - Start- und End-Zeile des Duplikats
 * - Anzahl der Tokens (für Similarity-Berechnung)
 * - Das zugehörige PsiElement
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class DuplicateCodeFragment {
    
    private final PsiFile file;
    private final int startLine;
    private final int endLine;
    private final int tokens;
    private final String code;
    private PsiElement psiElement;
    
    
    public DuplicateCodeFragment(@NotNull PsiFile file, 
                                 int startLine, 
                                 int endLine, 
                                 int tokens,
                                 @NotNull String code) {
        this.file = file;
        this.startLine = startLine;
        this.endLine = endLine;
        this.tokens = tokens;
        this.code = code;
    }
    
    
    @NotNull
    public PsiFile getFile() {
        return file;
    }
    
    
    public int getStartLine() {
        return startLine;
    }
    
    
    public int getEndLine() {
        return endLine;
    }
    
    
    public int getLineCount() {
        return endLine - startLine + 1;
    }
    
    
    public int getTokens() {
        return tokens;
    }
    
    @NotNull
    public String getCode() {
        return code;
    }
    
    public void setPsiElement(@Nullable PsiElement element) {
        this.psiElement = element;
    }
    
    @Nullable
    public PsiElement getPsiElement() {
        return psiElement;
    }
    
    @NotNull
    public String getFilePath() {
        return ReadAction.compute(() -> file.getVirtualFile().getPath());
    }
    
    @NotNull
    public String getFileName() {
        return ReadAction.compute(() -> file.getName());
    }
    
    @NotNull
    public String getLocationString() {
        return ReadAction.compute(() -> 
            file.getName() + ":" + startLine + "-" + endLine
        );
    }
    
    @NotNull
    public String getClassName() {
        return ReadAction.compute(() -> {
            if (psiElement == null) {
                return "<unknown>";
            }
            
            // Finde die umschließende Klasse
            PsiClass containingClass = PsiTreeUtil.getParentOfType(
                psiElement, 
                PsiClass.class
            );
            
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if (qualifiedName != null) {
                    // Verwende nur den einfachen Namen (ohne Package)
                    int lastDot = qualifiedName.lastIndexOf('.');
                    return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
                }
                return containingClass.getName() != null ? containingClass.getName() : "<anonymous>";
            }
            
            return "<unknown>";
        });
    }
    
    @NotNull
    public String getClassQualifiedLocationString() {
        return ReadAction.compute(() -> {
            String className = getClassName();
            String fileName = file.getName();
            return className + " (" + fileName + ":" + startLine + "-" + endLine + ")";
        });
    }
    
    public void navigateToSource() {
        ReadAction.run(() -> {
            Project project = file.getProject();
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document != null && startLine > 0 && startLine <= document.getLineCount()) {
                int offset = document.getLineStartOffset(startLine - 1);
                OpenFileDescriptor descriptor = new OpenFileDescriptor(
                    project, 
                    file.getVirtualFile(), 
                    offset
                );
                descriptor.navigate(true);
            }
        });
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DuplicateCodeFragment that = (DuplicateCodeFragment) o;
        return startLine == that.startLine &&
               endLine == that.endLine &&
               Objects.equals(file, that.file);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(file, startLine, endLine);
    }
    
    @Override
    public String toString() {
        return ReadAction.compute(() -> 
            "DuplicateCodeFragment{" +
            "file=" + file.getName() +
            ", lines=" + startLine + "-" + endLine +
            ", tokens=" + tokens +
            '}'
        );
    }
}
