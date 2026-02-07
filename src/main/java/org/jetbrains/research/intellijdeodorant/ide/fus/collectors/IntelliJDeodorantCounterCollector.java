package org.jetbrains.research.intellijdeodorant.ide.fus.collectors;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.research.intellijdeodorant.core.ast.decomposition.cfg.ASTSlice;
import org.jetbrains.research.intellijdeodorant.ide.fus.IntelliJDeodorantLogger;

/**
 * KOMPATIBILITÄT FIX für IntelliJ 2025.2+
 * 
 * FUS (Feature Usage Statistics) wurde deaktiviert für Kompatibilität.
 * Alle Methoden sind jetzt No-Op Implementationen, die keine Exceptions werfen.
 * 
 * Das Plugin funktioniert vollständig ohne Statistiken.
 */
public class IntelliJDeodorantCounterCollector {
    private IntelliJDeodorantCounterCollector() {
        // No-op: Scheduler deaktiviert für Kompatibilität
        // Originaler Code verwendete JobScheduler für periodisches Logging
    }

    private static IntelliJDeodorantCounterCollector instance;

    // Dummy event group - wird nicht mehr verwendet, aber für API-Kompatibilität beibehalten
    private static final Object group = new Object();

    /**
     * No-Op: Statistiken deaktiviert
     * Originale Funktion: Logged wenn Refactorings gefunden wurden
     */
    public void refactoringFound(Project project, String name, Integer total) {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }

    /**
     * No-Op: Statistiken deaktiviert
     * Originale Funktion: Logged wenn Extract Method Refactoring angewendet wurde
     */
    public void extractMethodRefactoringApplied(Project project, ASTSlice slice, PsiMethod extractedMethod) {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }

    /**
     * No-Op: Statistiken deaktiviert
     * Originale Funktion: Logged wenn Move Method Refactoring angewendet wurde
     */
    public void moveMethodRefactoringApplied(Project project, Integer sourceAccessedMembers, Integer targetAccessedMembers,
                                             Integer methodLength, Integer methodParametersCount) {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }

    /**
     * No-Op: Statistiken deaktiviert
     * Originale Funktion: Logged wenn Extract Class Refactoring angewendet wurde
     */
    public void extractClassRefactoringApplied(Project project, Integer extractedFieldsCount, Integer extractedMethodsCount,
                                               Integer totalFieldsCountInOriginalClass, Integer totalMethodsCountInOriginalClass) {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }

    /**
     * No-Op: Statistiken deaktiviert
     * Originale Funktion: Logged wenn Type/State Checking Refactoring angewendet wurde
     */
    public void typeStateCheckingRefactoringApplied(Project project, Integer totalNumberOfCaseStatements,
                                                    Double averageNumberOfStatementsPerCase) {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }

    /**
     * Singleton instance - API-kompatibel aber ohne Funktionalität
     */
    public static IntelliJDeodorantCounterCollector getInstance() {
        if (instance == null) {
            instance = new IntelliJDeodorantCounterCollector();
        }
        return instance;
    }

    /**
     * No-Op: Scheduler-Task deaktiviert
     */
    private static void trackRegistered() {
        // No-op: Statistiken deaktiviert für Kompatibilität
    }
}
