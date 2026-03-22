package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Strategie 2: Extract and Pull Up Method.
 *
 * <p>Vorbedingungen (werden vom Handler geprüft):
 * <ul>
 *   <li>Mindestens eine der Duplikate liegt in einer anderen Klasse.</li>
 *   <li>Alle beteiligten Klassen sind in derselben Hierarchie.</li>
 *   <li>Die niedrigste gemeinsame Superklasse ist eine Klasse, die im Quellcode des Projekts liegt.</li>
 * </ul>
 *
 * <p>Schritte:
 * <ol>
 *   <li>ExtractMethod auf das erste Fragment anwenden (IntelliJ öffnet den Dialog).</li>
 *   <li>Nach dem Dialog: die extrahierte Methode via {@link PullUpProcessor} in die Superklasse hochziehen.</li>
 *   <li>Sichtbarkeit der extrahierten Methode auf {@code protected} setzen.</li>
 *   <li>IntelliJ ersetzt die übrigen Duplikate automatisch nach dem Pull-Up.</li>
 * </ol>
 */
public class ExtractAndPullUpStrategy extends DuplicateRefactoringStrategy {

    public ExtractAndPullUpStrategy(@NotNull Project project) {
        super(project);
    }

    @Override
    @NotNull
    public String getName() {
        return "Extract and Pull Up Method";
    }

    @Override
    @NotNull
    public String getDescription(@NotNull RefactoringContext context) {
        return super.getDescription(context)
                + "\nTarget superclass: "
                + (context.commonSuperClass != null ? context.commonSuperClass.getName() : "?")
                + "\n\nSteps:\n"
                + "  1. Extract Method (dialog will open)\n"
                + "  2. Pull up to " + (context.commonSuperClass != null ? context.commonSuperClass.getName() : "superclass") + "\n"
                + "  3. Set visibility to protected\n";
    }

    @Override
    public void execute(@NotNull RefactoringContext context) {
        PsiClass superClass = context.commonSuperClass;
        if (superClass == null) {
            showError("Could not determine the common superclass.");
            return;
        }

        PsiClass sourceClass = context.affectedClasses.get(0);

        ExtractMethodProcessor processor = runExtractMethod(context.fragments.get(0), sourceClass);
        if (processor == null) return;
        performPullUp(processor.getExtractedMethod(), sourceClass, superClass);
    }

    /**
     * Zieht die extrahierte Methode in die Superklasse hoch und setzt die Sichtbarkeit auf protected.
     */
    private void performPullUp(@NotNull PsiMethod method,
                                @NotNull PsiClass sourceClass,
                                @NotNull PsiClass superClass) {
        WriteCommandAction.runWriteCommandAction(project, "Set Method Visibility", null, () -> {
            PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true);
        }, method.getContainingFile());

        MemberInfo memberInfo = new MemberInfo(method);
        memberInfo.setChecked(true);

        PullUpProcessor pullUpProcessor = new PullUpProcessor(
                sourceClass,
                superClass,
                new MemberInfo[]{memberInfo},
                new DocCommentPolicy(DocCommentPolicy.ASIS));

        pullUpProcessor.run();
    }
}
