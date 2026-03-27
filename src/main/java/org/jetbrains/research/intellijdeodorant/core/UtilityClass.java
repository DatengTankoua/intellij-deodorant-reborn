package org.jetbrains.research.intellijdeodorant.core;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.core.ast.FieldInstructionObject;
import org.jetbrains.research.intellijdeodorant.core.ast.decomposition.cfg.PlainVariable;

import java.util.List;

public final class UtilityClass {
    private UtilityClass() {}

    @Nullable
    public static FieldInstructionObject getFieldInstructionObject(PlainVariable variable, List<FieldInstructionObject> fieldInstructions) {
        for (FieldInstructionObject fieldInstruction : fieldInstructions) {
            PsiElement psiElement = fieldInstruction.getElement();
            if (psiElement instanceof PsiField) {
                PsiField psiField = (PsiField) psiElement;
                if (psiField.getName().equals(variable.getName()))
                    return fieldInstruction;
            }
        }
        return null;
    }
}
