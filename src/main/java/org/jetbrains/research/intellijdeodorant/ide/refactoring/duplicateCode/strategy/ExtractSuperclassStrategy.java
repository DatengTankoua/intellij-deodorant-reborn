package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Strategie 3: Extract Superclass.
 */
public class ExtractSuperclassStrategy extends DuplicateRefactoringStrategy {

    public ExtractSuperclassStrategy(@NotNull Project project) {
        super(project);
    }

    @Override
    @NotNull
    public String getName() {
        return "Extract Superclass + Extract and Pull Up Method";
    }

    @Override
    @NotNull
    public String getDescription(@NotNull RefactoringContext context) {
        return super.getDescription(context)
                + "\nLowest common superclass (external): "
                + (context.commonSuperClass != null ? context.commonSuperClass.getQualifiedName() : "?")
                + "\n\nSteps:\n"
                + "  1. Create new class extending the external superclass\n"
                + "  2. Extract Method on first duplicate (dialog will open)\n"
                + "  3. Pull up extracted method into new class\n"
                + "  4. Set visibility to protected\n";
    }

    @Override
    public void execute(@NotNull RefactoringContext context) {
        PsiClass externalSuper = context.commonSuperClass;
        if (externalSuper == null) {
            showError("Could not determine the common superclass.");
            return;
        }

        PsiDirectory targetDir = chooseTargetDirectory(context);

        if (targetDir == null) {
            showError("Could not determine target directory for new superclass.");
            return;
        }

        String newClassName = askUserForClassName("ExtractedBase", targetDir,
                "Enter the name for the new intermediate superclass:",
                "Extract Superclass Name");

        if (newClassName == null) {
            showError("No name provided for the new superclass.");
            return;
        }

        PsiClass newSuperClass = createIntermediateSuperclass(newClassName, externalSuper, targetDir);
        if (newSuperClass == null) return;

        // Alle betroffenen Klassen auf die neue Superklasse umstellen.
        for (PsiClass affected : context.affectedClasses) {
            WriteCommandAction.runWriteCommandAction(project, "Reparent Class", null, () -> {
                reparentClass(affected, newSuperClass);
            });
        }

        RefactoringContext updatedContext = context;
        updatedContext.commonSuperClass = newSuperClass;
        updatedContext.superClassIsInSource = true;

        new ExtractAndPullUpStrategy(project).execute(updatedContext);
    }

    /**
     * Erstellt eine neue Klasse im Zielverzeichnis.
     */
    @Nullable
    private PsiClass createIntermediateSuperclass(@NotNull String newClassName,
                                                   @NotNull PsiClass externalSuper,
                                                   @NotNull PsiDirectory targetDir) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

        // Prüfen ob Klasse schon existiert
        PsiFile existingFile = targetDir.findFile(newClassName + ".java");
        if (existingFile != null) {
            showError("Class \"" + newClassName + "\" already exists in the target directory.");
            return null;
        }

        try {
            PsiClass[] result = new PsiClass[1];
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(
                    project, "Create Intermediate Superclass", null, (Runnable) () -> {
                        PsiClass newClass = factory.createClass(newClassName);
                        // extends externalSuper
                        PsiJavaCodeReferenceElement ref =
                                factory.createClassReferenceElement(externalSuper);
                        PsiReferenceList extendsList = newClass.getExtendsList();
                        if (extendsList != null) {
                            extendsList.add(ref);
                        }
                        PsiUtil.setModifierProperty(newClass, PsiModifier.PUBLIC, true);
                        PsiFile javaFile = PsiFileFactory.getInstance(project)
                                .createFileFromText(newClassName + ".java",
                                        com.intellij.lang.java.JavaLanguage.INSTANCE,
                                        newClass.getContainingFile().getText());
                        PsiFile added = (PsiFile) targetDir.add(javaFile);
                        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(added);
                        
                        // Hole die Klasse aus der neu erstellten Datei
                        for (PsiClass cls : com.intellij.psi.util.PsiTreeUtil
                                .findChildrenOfType(added, PsiClass.class)) {
                            if (newClassName.equals(cls.getName())) {
                                result[0] = cls;
                                break;
                            }
                        }
                    });
            if (result[0] == null) {
                showError("Could not create class \"" + newClassName + "\".");
            }
            return result[0];
        } catch (Exception e) {
            showError("Failed to create intermediate superclass: " + e.getMessage());
            return null;
        }
    }

    /**
     * Wählt das Verzeichnis der ersten betroffenen Klasse als Ziel.
     */
    @Nullable
    private PsiDirectory chooseTargetDirectory(@NotNull RefactoringContext context) {
        if (context.affectedClasses.isEmpty()) return null;
        PsiFile file = context.affectedClasses.get(0).getContainingFile();
        if (file == null) return null;
        return file.getContainingDirectory();
    }

    /**
     * Setzt die neue Superklasse für die gegebene Unterklasse.
     */
    void reparentClass(PsiClass subClass, PsiClass newSuperClass) {
        PsiReferenceList extendsList = subClass.getExtendsList();
        if (extendsList != null) {

            for (PsiJavaCodeReferenceElement ref : extendsList.getReferenceElements()) {
                ref.delete();
            }
            
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(subClass.getProject());
            PsiJavaCodeReferenceElement newRef = factory.createClassReferenceElement(newSuperClass);
            extendsList.add(newRef);
        }
    }
}
