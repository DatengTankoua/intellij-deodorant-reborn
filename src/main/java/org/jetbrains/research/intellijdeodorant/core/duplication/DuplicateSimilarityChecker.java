package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Prüft ob die Fragmente einer Gruppe nach Extract Method noch als Duplikate
 * erkennbar wären (strukturelle und typbasierte Ähnlichkeit).
 */
class DuplicateSimilarityChecker {

    private static final Logger LOG = Logger.getInstance(DuplicateSimilarityChecker.class);

    @FunctionalInterface
    private interface FragmentExtractor {
        Object extract(DuplicateCodeFragment fragment, PsiFile psiFile);
    }

    /**
     * Erstellt eine neue Gruppe für Fragmente, deren Typ- oder Struktursignatur von der Mehrheit der Gruppe abweicht.
     *
     * @return Anzahl der neu erstellten Gruppen
     */
    static int validate(@NotNull Set<DuplicateCodeGroup> groups) {
        int byType      = validateByMajority(groups, DuplicateSimilarityChecker::extractVariableTypes, "type");
        int byStructure = validateByMajority(groups, (f, p) -> extractCodeSignature(f), "structure");
        return byType + byStructure;
    }

    private static int validateByMajority(@NotNull Set<DuplicateCodeGroup> groups,
                                           @NotNull FragmentExtractor extractor,
                                           @NotNull String label) {
        int countNewGroup = 0;
        List<DuplicateCodeGroup> newGroups = new ArrayList<>();

        for (DuplicateCodeGroup group : groups) {
            if (group.getOccurrences() < 2) continue;

            Map<Object, Integer> counts = new HashMap<>();
            Map<DuplicateCodeFragment, Object> props = new HashMap<>();

            for (DuplicateCodeFragment f : group.getFragments()) {
                Object prop = ReadAction.compute(() -> extractor.extract(f, f.getFile()));
                props.put(f, prop);
                counts.merge(prop, 1, Integer::sum);
            }

            Object majority = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
            if (majority == null) continue;

            // Abweichende Fragmente nach ihrer Eigenschaft gruppieren
            Map<Object, List<DuplicateCodeFragment>> outliersByProp = new HashMap<>();
            for (Map.Entry<DuplicateCodeFragment, Object> e : props.entrySet()) {
                if (!e.getValue().equals(majority)) {
                    group.removeFragment(e.getKey());
                    outliersByProp.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
                    LOG.info("Removed fragment (" + label + " mismatch): " + e.getKey().getLocationString());
                }
            }

            // Für jeden abweichenden Eigenschaftswert eine neue Gruppe erstellen (wenn ≥2 Fragmente)
            for (Map.Entry<Object, List<DuplicateCodeFragment>> entry : outliersByProp.entrySet()) {
                List<DuplicateCodeFragment> outliers = entry.getValue();
                if (outliers.size() >= 2) {
                    DuplicateCodeGroup newGroup = new DuplicateCodeGroup(group.getTokens());
                    outliers.forEach(newGroup::addFragment);
                    newGroups.add(newGroup);
                    countNewGroup++;
                    LOG.info("Created new group (" + label + ") with " + outliers.size() + " fragments");
                }
            }
        }

        groups.addAll(newGroups);
        return countNewGroup;
    }

    private static String extractCodeSignature(@NotNull DuplicateCodeFragment fragment) {
        PsiElement[] stmts = fragment.getStatements();
        if (stmts == null || stmts.length == 0) return "";
        StringBuilder sig = new StringBuilder();
        for (PsiElement stmt : stmts) {
            PsiTreeUtil.processElements(stmt, el -> {
                if (el instanceof PsiMethodCallExpression) {
                    PsiMethod m = ((PsiMethodCallExpression) el).resolveMethod();
                    if (m != null)
                        sig.append("M:").append(m.getName()).append(":")
                           .append(((PsiMethodCallExpression) el).getArgumentList().getExpressionCount()).append(";");
                } else if (el instanceof PsiBinaryExpression) {
                    sig.append("OP:").append(((PsiBinaryExpression) el).getOperationTokenType()).append(";");
                } else if (el instanceof PsiAssignmentExpression) {
                    sig.append("A;");
                }
                return true;
            });
        }
        return sig.toString();
    }

    private static String extractVariableTypes(@NotNull DuplicateCodeFragment fragment, @NotNull PsiFile psiFile) {
        PsiElement[] stmts = fragment.getStatements();
        if (stmts == null || stmts.length == 0) return "";
        List<String> types = new ArrayList<>();
        for (PsiElement stmt : stmts) {
            PsiTreeUtil.processElements(stmt, el -> {
                if (el instanceof PsiReferenceExpression) {
                    PsiElement resolved = ((PsiReferenceExpression) el).resolve();
                    if (resolved instanceof PsiVariable) addType(types, (PsiVariable) resolved);
                } else if (el instanceof PsiVariable) {
                    addType(types, (PsiVariable) el);
                }
                return true;
            });
        }
        Collections.sort(types);
        return String.join(";", types);
    }

    private static void addType(@NotNull List<String> list, @NotNull PsiVariable var) {
        PsiType type = var.getType();
        if (type != null) list.add(type.getCanonicalText());
    }
}
