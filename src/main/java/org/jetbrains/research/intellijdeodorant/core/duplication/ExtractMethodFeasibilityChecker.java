package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Prüft ob Extract Method auf den Fragmenten einer Gruppe ausführbar ist.
 */
class ExtractMethodFeasibilityChecker {

    private static final Logger LOG = Logger.getInstance(ExtractMethodFeasibilityChecker.class);

    /**
     * Führt alle Ausführbarkeits-Checks durch und entfernt ungültige Fragmente/Gruppen.
     *
     * @return Anzahl der insgesamt entfernten Gruppen
     */
    static int validate(@NotNull Set<DuplicateCodeGroup> groups) {
        
        int byFeasibility = 0;
        for (DuplicateCodeGroup group : groups) {
            for (DuplicateCodeFragment f : new ArrayList<>(group.getFragments())) {
                if (f.getLineCount() >= 7) {
                    if (!isRefactorable(f)) {
                        group.removeFragment(f);
                        byFeasibility++;
                        LOG.info("Removed fragment (not refactorable): " + f);
                        break; // Wenn eines der Fragmente nicht refactorable ist, ist die ganze Gruppe ungültig
                    }
                }
                else {
                    group.removeFragment(f);
                    byFeasibility++;
                    LOG.info("Removed fragment (too short for Extract Method): " + f);
                    break; // Wenn eines der Fragmente zu kurz ist, ist die ganze Gruppe ungültig
                }
            }
        }
        return byFeasibility;
    }
    
    private static boolean isRefactorable(@NotNull DuplicateCodeFragment fragment) {
        return ReadAction.compute(() -> {
            try {
                PsiFile psiFile = fragment.getFile();
                PsiElement[] stmts = fragment.getStatements();
                if (psiFile == null || stmts == null || stmts.length == 0) return false;
                ExtractMethodProcessor processor = new ExtractMethodProcessor(
                    psiFile.getProject(), null, stmts, null, "RefactoringTest", null, null
                );
                return processor.prepare();
            } catch (Exception e) {
                return false;
            }
        });
    }
}
