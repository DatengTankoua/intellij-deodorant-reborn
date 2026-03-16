package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
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
        int byOutputVar = filterMultipleOutputVariables(groups);
        int byScope     = filterInconsistentOutputVariableScope(groups);
        int byCtrlFlow  = filterUnmatchedControlFlow(groups);

        // Mindestlänge + IntelliJ ExtractMethodProcessor (Tsantalis & Chatzigeorgiou 2011)
        int byFeasibility = 0;
        for (DuplicateCodeGroup group : groups) {
            for (DuplicateCodeFragment f : new ArrayList<>(group.getFragments())) {
                if (f.getLineCount() < 7 || !isRefactorable(f)) {
                    group.removeFragment(f);
                    byFeasibility++;
                }
            }
        }
        return byOutputVar + byScope + byCtrlFlow + byFeasibility;
    }


    private static int filterMultipleOutputVariables(@NotNull Set<DuplicateCodeGroup> groups) {
        int before = groups.size();
        groups.removeIf(group -> {
            boolean hasMultiple = group.getFragments().stream()
                .anyMatch(f -> ReadAction.compute(() -> collectOutputVariables(f)).size() >= 2);
            if (hasMultiple)
                LOG.info("Removed group (multiple output variables): " + group);
            return hasMultiple;
        });
        return before - groups.size();
    }

    /**
     * Sammelt alle Output-Variablen eines Fragments:
     */
    static Set<PsiVariable> collectOutputVariables(@NotNull DuplicateCodeFragment fragment) {
        PsiElement[] stmts = fragment.getStatements();
        if (stmts == null || stmts.length == 0) return Collections.emptySet();

        int fragStart = stmts[0].getTextRange().getStartOffset();
        int fragEnd   = stmts[stmts.length - 1].getTextRange().getEndOffset();
        TextRange fragRange = new TextRange(fragStart, fragEnd);

        PsiMethod method = PsiTreeUtil.getParentOfType(stmts[0], PsiMethod.class);
        if (method == null || method.getBody() == null) return Collections.emptySet();

        Set<PsiVariable> modifiedInFragment = new HashSet<>();

        // Zuweisungen im Fragment: x = ..., x += ..., x -= ..., etc.
        for (PsiAssignmentExpression assign : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiAssignmentExpression.class)) {
            if (!fragRange.contains(assign.getTextRange())) continue;
            PsiExpression lhs = assign.getLExpression();
            if (lhs instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) lhs).resolve();
                if (resolved instanceof PsiLocalVariable) modifiedInFragment.add((PsiVariable) resolved);
            }
        }

        // Inkrement/Dekrement im Fragment: x++, x--, ++x, --x
        for (PsiUnaryExpression unary : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiUnaryExpression.class)) {
            if (!fragRange.contains(unary.getTextRange())) continue;
            IElementType op = unary.getOperationTokenType();
            if (op != JavaTokenType.PLUSPLUS && op != JavaTokenType.MINUSMINUS) continue;
            PsiExpression operand = unary.getOperand();
            if (operand instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) operand).resolve();
                if (resolved instanceof PsiLocalVariable) modifiedInFragment.add((PsiVariable) resolved);
            }
        }

        // Deklarationen mit Initialisierung im Fragment: int x = compute();
        for (PsiLocalVariable var : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiLocalVariable.class)) {
            if (fragRange.contains(var.getTextRange()) && var.getInitializer() != null)
                modifiedInFragment.add(var);
        }

        // Output-Variable: im Fragment geändert UND nach dem Fragment benutzt
        Set<PsiVariable> outputVars = new HashSet<>();
        for (PsiVariable var : modifiedInFragment) {
            boolean usedAfter = PsiTreeUtil.findChildrenOfType(method.getBody(), PsiReferenceExpression.class)
                .stream()
                .anyMatch(ref -> ref.getTextRange().getStartOffset() > fragEnd && ref.resolve() == var);
            if (usedAfter) outputVars.add(var);
        }
        return outputVars;
    }

    private static int filterInconsistentOutputVariableScope(@NotNull Set<DuplicateCodeGroup> groups) {
        int removed = 0;
        List<DuplicateCodeGroup> newGroups = new ArrayList<>();

        for (DuplicateCodeGroup group : groups) {
            Map<DuplicateCodeFragment, String> scopeMap = new LinkedHashMap<>();

            for (DuplicateCodeFragment f : group.getFragments()) {
                Set<PsiVariable> outputs = ReadAction.compute(() -> collectOutputVariables(f));
                if (outputs.size() != 1) continue;

                PsiVariable outVar = outputs.iterator().next();
                String scope = (outVar instanceof PsiLocalVariable) ? "method"
                             : (outVar instanceof PsiField)         ? "class"
                             : null;
                if (scope != null) scopeMap.put(f, scope);
            }

            if (scopeMap.size() < 2) continue;

            // Mehrheitsprinzip: Scope mit den meisten Vorkommen gewinnt
            Map<String, Integer> counts = new HashMap<>();
            for (String s : scopeMap.values()) counts.merge(s, 1, Integer::sum);
            String majority = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
            if (majority == null) continue;

            // Fragmente mit abweichendem Scope nach Scope-Typ gruppieren
            Map<String, List<DuplicateCodeFragment>> outliersByScope = new HashMap<>();
            for (Map.Entry<DuplicateCodeFragment, String> e : scopeMap.entrySet()) {
                if (!e.getValue().equals(majority)) {
                    group.removeFragment(e.getKey());
                    removed++;
                    outliersByScope.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
                    LOG.info("Removed fragment (output variable scope mismatch): " + e.getKey().getLocationString());
                }
            }

            // Für jeden abweichenden Scope eine neue Gruppe erstellen (wenn ≥2 Fragmente)
            for (Map.Entry<String, List<DuplicateCodeFragment>> entry : outliersByScope.entrySet()) {
                List<DuplicateCodeFragment> outliers = entry.getValue();
                if (outliers.size() >= 2) {
                    DuplicateCodeGroup newGroup = new DuplicateCodeGroup(group.getTokens());
                    outliers.forEach(newGroup::addFragment);
                    newGroups.add(newGroup);
                    LOG.info("Created new group for scope '" + entry.getKey() + "' with " + outliers.size() + " fragments");
                }
            }
        }

        groups.addAll(newGroups);
        return removed;
    }

    private static int filterUnmatchedControlFlow(@NotNull Set<DuplicateCodeGroup> groups) {
        int before = groups.size();
        groups.removeIf(group -> {
            boolean hasUnmatched = group.getFragments().stream()
                .anyMatch(f -> ReadAction.compute(() -> hasUnmatchedJump(f)));
            if (hasUnmatched)
                LOG.info("Removed group (unmatched control flow): " + group);
            return hasUnmatched;
        });
        return before - groups.size();
    }

    // Hilfsmethode: Prüft ob ein Fragment nicht-lokalen Kontrollfluss enthält (break/continue zu außerhalb liegenden Zielen)
    private static boolean hasUnmatchedJump(@NotNull DuplicateCodeFragment fragment) {
        PsiElement[] stmts = fragment.getStatements();
        if (stmts == null || stmts.length == 0) return false;

        TextRange fragRange = new TextRange(
            stmts[0].getTextRange().getStartOffset(),
            stmts[stmts.length - 1].getTextRange().getEndOffset()
        );

        boolean hasBreak = false, hasContinue = false, hasReturn = false;

        for (PsiElement stmt : stmts) {
            for (PsiBreakStatement brk : PsiTreeUtil.findChildrenOfType(stmt, PsiBreakStatement.class)) {
                PsiStatement target = brk.findExitedStatement();
                if (target == null || !fragRange.contains(target.getTextRange())) { hasBreak = true; break; }
            }
            for (PsiContinueStatement cont : PsiTreeUtil.findChildrenOfType(stmt, PsiContinueStatement.class)) {
                PsiStatement target = cont.findContinuedStatement();
                if (target == null || !fragRange.contains(target.getTextRange())) { hasContinue = true; break; }
            }
            if (!PsiTreeUtil.findChildrenOfType(stmt, PsiReturnStatement.class).isEmpty()) hasReturn = true;
        }

        // Nur problematisch wenn zwei unterschiedliche Sprungtypen vorhanden sind
        int jumpTypes = (hasBreak ? 1 : 0) + (hasContinue ? 1 : 0) + (hasReturn ? 1 : 0);
        return jumpTypes >= 2;
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
