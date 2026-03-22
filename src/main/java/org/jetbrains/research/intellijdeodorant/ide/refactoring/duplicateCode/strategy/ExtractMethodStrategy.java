package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Strategie 1: Extract Method in derselben Klasse.
 */
public class ExtractMethodStrategy extends DuplicateRefactoringStrategy {

    public ExtractMethodStrategy(@NotNull Project project) {
        super(project);
    }

    @Override
    @NotNull
    public String getName() {
        return "Extract Method (same class)";
    }

    @Override
    public void execute(@NotNull RefactoringContext context) {
        //IntelliJ findet und ersetzt alle weiteren Duplikate innerhalb der Klasse automatisch.
        runExtractMethod(context.fragments.get(0), context.affectedClasses.get(0));
    }
}
