package org.jetbrains.research.intellijdeodorant.core.ast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.inheritance.InheritanceTree;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

public final class UtilityClass {
    private UtilityClass() {}

    @Nullable
    public static PsiMethodCallExpression getPsiMethodCallExpression(PsiElement statement) {
        PsiMethodCallExpression methodInvocation = null;
        if (statement instanceof PsiReturnStatement) {
            PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            if (returnStatement.getReturnValue() instanceof PsiMethodCallExpression) {
                methodInvocation = (PsiMethodCallExpression) returnStatement.getReturnValue();
            }
        } else if (statement instanceof PsiExpressionStatement) {
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
            if (expressionStatement.getExpression() instanceof PsiMethodCallExpression) {
                methodInvocation = (PsiMethodCallExpression) expressionStatement.getExpression();
            }
        }
        return methodInvocation;
    }

    @NotNull
    public static List<String> getStrings(InheritanceTree tree) {
        DefaultMutableTreeNode rootNode = tree.getRootNode();
        DefaultMutableTreeNode leaf = rootNode.getFirstLeaf();
        List<String> inheritanceHierarchySubclassNames = new ArrayList<>();
        while (leaf != null) {
            inheritanceHierarchySubclassNames.add((String) leaf.getUserObject());
            leaf = leaf.getNextLeaf();
        }
        return inheritanceHierarchySubclassNames;
    }
}
