package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

import static org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.RefactoringLogger.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy.*;

import java.util.List;

/**
 * Einstiegspunkt fur das Refactoring von Duplikaten.
 */
public class DuplicateCodeRefactoringHandler {

    private final Project project;

    public DuplicateCodeRefactoringHandler(@NotNull Project project) {
        this.project = project;
    }

    /** Hauptmethode: analysiert die Gruppe und startet das Refactoring. */
    public void performRefactoring(@NotNull DuplicateCodeGroup group) {
        if (group.getFragments().isEmpty()) {
            Messages.showErrorDialog(project, "No duplicates found to refactor.", "Refactoring Error");
            return;
        }

        DuplicateRefactoringStrategy.RefactoringContext context =
                ReadAction.compute(() -> analyzeGroup(group));

        if (context == null) {
            Messages.showErrorDialog(project, "Could not analyze duplicates.", "Refactoring Error");
            return;
        }

        DuplicateRefactoringStrategy strategy = selectStrategy(context);
        info("Selected strategy: " + strategy.getName());

        if (!showConfirmationDialog(context, strategy)) return;

        ApplicationManager.getApplication().invokeLater(() -> strategy.execute(context));
    }

    
    @Nullable
    private DuplicateRefactoringStrategy.RefactoringContext analyzeGroup(@NotNull DuplicateCodeGroup group) {
        DuplicateRefactoringStrategy.RefactoringContext ctx =
                DuplicateRefactoringStrategy.analyzeClasses(group);
        if (ctx == null) return null;

        if (!ctx.isSameClass) {
            ctx.commonSuperClass = findCommonSuperClass(ctx.affectedClasses);
            if (ctx.commonSuperClass != null) {
                ctx.superClassIsInSource = isInProjectSource(ctx.commonSuperClass);
            }
        }
        return ctx;
    }

    /**
     * Gibt die niedrigste gemeinsame Superklasse zuruck.
     */
    @Nullable
    private PsiClass findCommonSuperClass(@NotNull List<PsiClass> classes) {
        if (classes.size() < 2) return null;

        PsiClass first = classes.get(0);
        PsiClass current = first.getSuperClass();
        while (current != null) {
            String qn = current.getQualifiedName();
            if ("java.lang.Object".equals(qn)) break;

            boolean commonForAll = true;
            for (int i = 1; i < classes.size(); i++) {
                if (!InheritanceUtil.isInheritorOrSelf(classes.get(i), current, true)) {
                    commonForAll = false;
                    break;
                }
            }
            if (commonForAll) return current;
            current = current.getSuperClass();
        }
        return null;
    }

    /** Pruft, ob eine Klasse im Quellcode des Projekts (nicht in externer Lib) liegt. */
    private boolean isInProjectSource(@NotNull PsiClass cls) {
        PsiFile file = cls.getContainingFile();
        if (file == null) return false;
        com.intellij.openapi.vfs.VirtualFile vf = file.getVirtualFile();
        if (vf == null) return false;
        return ProjectFileIndex.getInstance(project).isInSourceContent(vf);
    }

    @NotNull
    private DuplicateRefactoringStrategy selectStrategy(
            @NotNull DuplicateRefactoringStrategy.RefactoringContext ctx) {

        if (ctx.isSameClass) {
            return new ExtractMethodStrategy(project);
        }
        if (ctx.commonSuperClass != null) {
            if (ctx.superClassIsInSource) {
                // Gemeinsame Superklasse im Projekt-Quellcode gefunden: Extract and Pull Up
                return new ExtractAndPullUpStrategy(project);
            } 
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Bestatigungsdialog
    // -----------------------------------------------------------------------

    private boolean showConfirmationDialog(
            @NotNull DuplicateRefactoringStrategy.RefactoringContext context,
            @NotNull DuplicateRefactoringStrategy strategy) {

        String message = "Extract Method Refactoring for Duplicated Code\n\n"
                + strategy.getDescription(context)
                + "\nContinue with refactoring?";

        int result = Messages.showOkCancelDialog(
                project, message,
                "Extract Method Refactoring",
                "Proceed", "Cancel",
                Messages.getQuestionIcon());

        return result == Messages.OK;
    }
}
