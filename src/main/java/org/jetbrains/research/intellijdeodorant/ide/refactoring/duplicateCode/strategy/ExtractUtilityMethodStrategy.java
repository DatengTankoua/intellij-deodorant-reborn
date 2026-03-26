package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.refactoring.util.duplicates.Match;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateSimilarityChecker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strategie 4: Extract Utility Method.
 */
public class ExtractUtilityMethodStrategy extends DuplicateRefactoringStrategy {

    public ExtractUtilityMethodStrategy(@NotNull Project project) {
        super(project);
    }

    @Override
    @NotNull
    public String getName() {
        return "Extract Utility Method";
    }

    @Override
    @NotNull
    public String getDescription(@NotNull RefactoringContext context) {
        return super.getDescription(context)
                + "\nUtility class: "
                + "\n\nSteps:\n"
                + "  1. Create/use utility class\n"
                + "  2. Extract Method (dialog will open)\n"
                + "  3. Move extracted method to utility class as public static\n";
    }

    @Override
    public void execute(@NotNull RefactoringContext context) {
        PsiClass sourceClass = context.affectedClasses.get(0);

        PsiDirectory targetDir = chooseTargetDirectory(context);

        if (targetDir == null) {
            showError("Could not determine target directory for new utility class.");
            return;
        }
        String utilClassName = askUserForClassName("UtilityClass", targetDir,
                "Enter the name for the utility class:",
                "Utility Class Name");

        if (utilClassName == null) {
            showError("No name provided for the utility class.");
            return;
        }

        PsiClass utilClass = getOrCreateUtilityClass(utilClassName, sourceClass);
        if (utilClass == null) return;

        ExtractMethodProcessor processor = runExtractMethod(context.fragments.get(0), sourceClass);
        if (processor == null) return;

        PsiElement[] elements = processor.getElements();

        Set<PsiClass> otherClasses = new LinkedHashSet<>();
        for (int i = 1; i < context.fragments.size(); i++) {
            PsiClass cls = context.fragmentToClass.get(context.fragments.get(i));
            if (cls != null) otherClasses.add(cls);
        }

        List<Match> preFoundMatches = otherClasses.isEmpty()
                ? java.util.Collections.emptyList()
                : DuplicateSimilarityChecker.findExactMatches(processor, elements, otherClasses);

        // Gefundene Matches ersetzen, dann Methode verschieben
        schedulePostExtractMove(processor, utilClass, preFoundMatches, sourceClass);
    }

    /**
     * Erstellt die Utility-Klasse oder gibt eine bereits vorhandene zurück.
     */
    @Nullable
    private PsiClass getOrCreateUtilityClass(@NotNull String className, @NotNull PsiClass referenceClass) {
        PsiFile refFile = referenceClass.getContainingFile();
        if (refFile == null) {
            showError("Could not determine source file of the affected class.");
            return null;
        }
        PsiDirectory dir = refFile.getContainingDirectory();
        if (dir == null) {
            showError("Could not determine source directory.");
            return null;
        }

        PsiFile existing = dir.findFile(className + ".java");
        if (existing instanceof PsiJavaFile) {
            for (PsiClass cls : ((PsiJavaFile) existing).getClasses()) {
                if (className.equals(cls.getName())) return cls;
            }
        }

        PsiClass[] result = {null};
        try {
            WriteCommandAction.runWriteCommandAction(project, "Create Utility Class", null, (Runnable) () -> {
                // Package aus Referenzklasse
                String packageName = ((PsiJavaFile) refFile).getPackageName();
                String fileContent = (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n")
                        + "public final class " + className + " {\n"
                        + "    private " + className + "() {}\n"
                        + "}\n";

                PsiFile javaFile = PsiFileFactory.getInstance(project)
                        .createFileFromText(className + ".java",
                                com.intellij.lang.java.JavaLanguage.INSTANCE,
                                fileContent);
                PsiFile added = (PsiFile) dir.add(javaFile);
                JavaCodeStyleManager.getInstance(project).optimizeImports(added);

                for (PsiClass cls : com.intellij.psi.util.PsiTreeUtil
                        .findChildrenOfType(added, PsiClass.class)) {
                    if (className.equals(cls.getName())) {
                        result[0] = cls;
                        break;
                    }
                }
            });
        } catch (Exception e) {
            showError("Failed to create utility class: " + e.getMessage());
            return null;
        }

        if (result[0] == null) {
            showError("Could not create utility class \"" + className + "\".");
        }
        return result[0];
    }

    /**
     * Wartet auf die extrahierte Methode, ersetzt die vorher gefundenen Matches
     * in anderen Klassen und verschiebt die Methode in die Utility-Klasse.     
     */
    private void schedulePostExtractMove(@NotNull ExtractMethodProcessor processor,
                                          @NotNull PsiClass utilClass,
                                          @NotNull List<Match> preFoundMatches,
                                          @NotNull PsiClass sourceClass) {

        ApplicationManager.getApplication().invokeLater(() -> {
            PsiMethod extracted = processor.getExtractedMethod();
            if (extracted == null || !extracted.isValid()) {
                return;
            }
            // Macht die Methode {static public} 
            WriteCommandAction.runWriteCommandAction(project, "Prepare Method for Cross-Class", null, () -> {
                PsiUtil.setModifierProperty(extracted, PsiModifier.STATIC, true);
                PsiUtil.setModifierProperty(extracted, PsiModifier.PRIVATE, false);
                PsiUtil.setModifierProperty(extracted, PsiModifier.PUBLIC, true);
            }, extracted.getContainingFile());

            // Vorher gefundene Matches in anderen Klassen ersetzen
            if (!preFoundMatches.isEmpty()) {
                WriteCommandAction.runWriteCommandAction(project, "Replace Duplicates", null, () -> {
                    for (Match match : preFoundMatches) {
                        try {
                            PsiElement startElement = match.getMatchStart();
                            PsiElement parentBlock = startElement.getParent();
                            
                            // Index des Duplikats im Block finden
                            PsiElement[] children = parentBlock.getChildren();
                            int statementIndex = -1;
                            for (int i = 0; i < children.length; i++) {
                                if (children[i] == startElement) {
                                    statementIndex = i;
                                    break;
                                }
                            }

                            //INTELLIJ ERSETZEN LASSEN
                            processor.prepareSignature(match);
                            processor.processMatch(match);
                            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                            String fqcn = sourceClass.getQualifiedName();

                            if (statementIndex != -1 && fqcn != null) {

                                PsiElement newStatement = parentBlock.getChildren()[statementIndex];
                                
                                PsiMethodCallExpression call = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
                                        newStatement, PsiMethodCallExpression.class);

                                if (call != null && call.getMethodExpression().getQualifierExpression() == null) {
                                    PsiExpression qualifier = factory.createExpressionFromText(fqcn, call);
                                    call.getMethodExpression().setQualifierExpression(qualifier);
                                }
                            }
                        } catch (Exception e) {
                            // ungültigen Match überspringen
                        }
                    }
                });
            }

            // Methode in Utility-Klasse verschieben
            moveMemberToUtilityClass(extracted, utilClass);
        }, ModalityState.NON_MODAL);
    }

    /**
     * verschiebt die Methode in die Utility-Klasse.
     */
    private void moveMemberToUtilityClass(@NotNull PsiMethod method,
                                           @NotNull PsiClass utilClass) {
        String targetQualifiedName = utilClass.getQualifiedName();
        if (targetQualifiedName == null) {
            showError("Utility class has no qualified name.");
            return;
        }

        MoveMembersOptions options = new MoveMembersOptions() {
            @Override public PsiMember[] getSelectedMembers() { return new PsiMember[]{method}; }
            @Override public String getTargetClassName()      { return targetQualifiedName; }
            @Override public String getMemberVisibility()     { return PsiModifier.PUBLIC; }
            @Override public boolean makeEnumConstant()       { return false; }
        };

        MoveMembersProcessor moveProcessor = new MoveMembersProcessor(project, options);
        moveProcessor.run();
    }
}
