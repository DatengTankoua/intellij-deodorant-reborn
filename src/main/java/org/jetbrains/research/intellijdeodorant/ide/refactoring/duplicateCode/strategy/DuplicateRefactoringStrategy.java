package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.IntelliJDeodorantBundle;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeFragment;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Abstrakte Basisklasse für alle Duplikat-Refactoring-Strategien.
 */
public abstract class DuplicateRefactoringStrategy {

    protected final Project project;

    protected DuplicateRefactoringStrategy(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public abstract String getName();

    @NotNull
    public String getDescription(@NotNull RefactoringContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Strategy: ").append(getName()).append("\n\n");
        sb.append("Duplicates: ").append(context.fragments.size()).append("\n");
        sb.append("Tokens: ").append(context.group.getTokens()).append("\n");
        sb.append("Avg Lines: ").append((int) context.group.getAverageLines()).append("\n\n");
        if (context.isSameClass) {
            sb.append("Class: ").append(context.affectedClasses.get(0).getName()).append("\n");
        } else {
            sb.append("Affected Classes:\n");
            for (PsiClass cls : context.affectedClasses) {
                sb.append("  \u2022 ").append(cls.getName()).append("\n");
            }
        }
        return sb.toString();
    }

    public abstract void execute(@NotNull RefactoringContext context);

    /**
     * Analysiert die am Duplikat beteiligten Klassen.
     */
    @Nullable
    public static RefactoringContext analyzeClasses(@NotNull DuplicateCodeGroup group) {
        List<DuplicateCodeFragment> fragments = group.getFragments();
        Set<PsiClass> affectedClasses = new LinkedHashSet<>();
        Map<DuplicateCodeFragment, PsiClass> fragmentToClass = new LinkedHashMap<>();

        for (DuplicateCodeFragment fragment : fragments) {
            PsiElement element = fragment.getPsiElement();
            if (element == null) return null;
            PsiClass cls = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (cls == null) return null;
            affectedClasses.add(cls);
            fragmentToClass.put(fragment, cls);
        }

        RefactoringContext ctx = new RefactoringContext();
        ctx.group = group;
        ctx.fragments = fragments;
        ctx.affectedClasses = new ArrayList<>(affectedClasses);
        ctx.fragmentToClass = fragmentToClass;
        ctx.isSameClass = affectedClasses.size() == 1;
        return ctx;
    }

    @Nullable
    private ExtractMethodProcessor buildProcessor(@NotNull DuplicateCodeFragment fragment,
                                                   @Nullable PsiClass targetClass) {
        PsiElement[] statements = fragment.getStatements();
        if (statements == null || statements.length == 0) {
            showError("No statements found to extract.");
            return null;
        }

        PsiFile file = fragment.getFile();
        int startOffset = statements[0].getTextRange().getStartOffset();
        Editor editor = FileEditorManager.getInstance(project)
                .openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile(), startOffset), true);

        PsiClass sourceClass = targetClass != null
                ? targetClass
                : PsiTreeUtil.getParentOfType(statements[0], PsiClass.class);
        if (sourceClass == null) {
            showError("Could not determine containing class.");
            return null;
        }

        ExtractMethodProcessor processor = new ExtractMethodProcessor(
                project, editor, statements, null,
                IntelliJDeodorantBundle.message("duplicated.code.refactoring.name"),
                "", HelpID.EXTRACT_METHOD);
        processor.setTargetClass(sourceClass);
        processor.setShowErrorDialogs(true);

        try {
            if (!processor.prepare()) {
                showError("Cannot prepare Extract Method refactoring.");
                return null;
            }
        } catch (PrepareFailedException e) {
            showError("Failed to prepare refactoring: " + e.getMessage());
            return null;
        }
        return processor;
    }

    /**
     * Öffnet den Extract-Method-Dialog für das Fragment (Strategie innerhalb derselben Klasse).
     * IntelliJ ersetzt Duplikate in derselben Klasse automatisch.
     */
    protected ExtractMethodProcessor runExtractMethod(@NotNull DuplicateCodeFragment fragment,
                                     @Nullable PsiClass targetClass) {
        ExtractMethodProcessor processor = buildProcessor(fragment, targetClass);
        if (processor == null) return null;
        PsiFile file = fragment.getFile();
        ApplicationManager.getApplication().invokeLater(() ->
                ExtractMethodHandler.invokeOnElements(project, processor, file, true));
        return processor;
    }


    protected void showError(@NotNull String message) {
        Messages.showErrorDialog(project, message, "Refactoring Error");
    }

    protected void showInfo(@NotNull String message) {
        Messages.showInfoMessage(project, message, "Refactoring Info");
    }

    /**
     * Analyseergebnis, das alle Strategien gemeinsam nutzen.
     */
    public static class RefactoringContext {
        public DuplicateCodeGroup group;
        public List<DuplicateCodeFragment> fragments;
        public List<PsiClass> affectedClasses;
        public Map<DuplicateCodeFragment, PsiClass> fragmentToClass;
        public boolean isSameClass;
        // Niedrigste gemeinsame Superklasse (kann null sein)
        public PsiClass commonSuperClass;
        
        public boolean superClassIsInSource;
    }
}
