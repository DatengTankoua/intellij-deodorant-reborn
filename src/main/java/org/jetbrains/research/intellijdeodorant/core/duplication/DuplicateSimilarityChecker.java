package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.ParametrizedDuplicates;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder.MatchType;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.refactoring.util.duplicates.VariableReturnValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
       
        // Strukturelle Duplikatsprüfung via DuplicatesFinder
        List<DuplicateCodeGroup> newGroups = new ArrayList<>();
        for (DuplicateCodeGroup g : new ArrayList<>(groups)) {
            duplicateSimilarityChecker(g, newGroups);
        }
        groups.addAll(newGroups);

        return newGroups.size();
    }

    /**
     * Prüft via DuplicatesFinder ob die Fragmente strukturell übereinstimmen.
     * Das erste Fragment dient als Anker: gefundene Duplikate bleiben in der Gruppe,
     * restliche Fragmente werden in eine neue Gruppe verschoben und rekursiv geprüft.
     */
    private static void duplicateSimilarityChecker(@NotNull DuplicateCodeGroup group,
                                                    @NotNull List<DuplicateCodeGroup> newGroupsOut) {
        List<DuplicateCodeFragment> fragments = new ArrayList<>(group.getFragments());
        if (fragments.size() < 2) return;

        DuplicateCodeFragment anchor = fragments.get(0);

        Set<DuplicateCodeFragment> confirmed = ReadAction.compute(() -> {
            // Typ-1: exakte strukturelle Duplikate via DuplicatesFinder (cross-file möglich)
            Set<DuplicateCodeFragment> matches = new HashSet<>(findMatchingFragments(anchor, fragments));
            // Typ-2: auf Fragmente derselben PSI-Klasse wie der Anker beschränken
            List<DuplicateCodeFragment> sameClass = filterFragmentsByClass(anchor, fragments);
            if (sameClass.size() >= 2) {
                matches.addAll(findParametrizedMatches(anchor, sameClass));
            }
            return matches;
        });

        extractNonStructuralDuplicates(group, newGroupsOut, fragments, anchor, confirmed);
    }

    private static void extractNonStructuralDuplicates(DuplicateCodeGroup group, List<DuplicateCodeGroup> newGroupsOut,
            List<DuplicateCodeFragment> fragments, DuplicateCodeFragment anchor, Set<DuplicateCodeFragment> confirmed) {
        // Fragmente, die kein strukturelles Duplikat des Ankers sind, auslagern
        List<DuplicateCodeFragment> outliers = new ArrayList<>();
        for (DuplicateCodeFragment f : fragments) {
            if (f != anchor && !confirmed.contains(f)) {
                group.removeFragment(f);
                outliers.add(f);
                LOG.info("Moved fragment to new group (not a structural duplicate of anchor): " + f.getLocationString());
            }
        }

        // Neue Gruppe für Ausreißer erstellen und rekursiv prüfen
        if (outliers.size() >= 2) {
            DuplicateCodeGroup newGroup = new DuplicateCodeGroup(outliers, group.getTokens());
            newGroupsOut.add(newGroup);
            duplicateSimilarityChecker(newGroup, newGroupsOut);
            LOG.info("Created new group for structural outliers: " + newGroup.getFirstFragment().getLocationString() + " and " + newGroup.getFragments().size() + " fragments");
        }
    }

    /**
     * Filtert Fragmente auf jene, die dieselbe PSI-Klasse wie der Anker teilen.
     * Der Anker selbst ist immer enthalten.
     */
    private static List<DuplicateCodeFragment> filterFragmentsByClass(
            @NotNull DuplicateCodeFragment anchor,
            @NotNull List<DuplicateCodeFragment> fragments) {
        PsiElement[] anchorStmts = anchor.getStatements();
        if (anchorStmts == null || anchorStmts.length == 0) return Collections.singletonList(anchor);
        PsiClass anchorClass = PsiTreeUtil.getParentOfType(anchorStmts[0], PsiClass.class);
        if (anchorClass == null) return Collections.singletonList(anchor);
        List<DuplicateCodeFragment> result = new ArrayList<>();
        result.add(anchor);
        for (DuplicateCodeFragment f : fragments) {
            if (f == anchor) continue;
            PsiElement[] stmts = f.getStatements();
            if (stmts == null || stmts.length == 0) continue;
            PsiClass cls = PsiTreeUtil.getParentOfType(stmts[0], PsiClass.class);
            if (anchorClass.equals(cls)) result.add(f);
        }
        return result;
    }

    /** Sammelt alle PSI-Klassen, in denen die Fragmente der Gruppe liegen. */
    private static Set<PsiClass> collectClassesToSearch(
            @NotNull DuplicateCodeFragment anchor,
            @NotNull List<DuplicateCodeFragment> fragments) {
        Set<PsiClass> classes = new LinkedHashSet<>();
        PsiElement[] anchorStmts = anchor.getStatements();
        if (anchorStmts != null && anchorStmts.length > 0) {
            PsiClass cls = PsiTreeUtil.getParentOfType(anchorStmts[0], PsiClass.class);
            if (cls != null) classes.add(cls);
        }
        for (DuplicateCodeFragment f : fragments) {
            if (f == anchor) continue;
            PsiElement[] stmts = f.getStatements();
            if (stmts != null && stmts.length > 0) {
                PsiClass cls = PsiTreeUtil.getParentOfType(stmts[0], PsiClass.class);
                if (cls != null) classes.add(cls);
            }
        }
        return classes;
    }

    /** Erstellt und bereitet einen {@link InternalProcessor} vor; gibt {@code null} zurück wenn nicht möglich. */
    @Nullable
    private static InternalProcessor buildProcessor(@NotNull PsiFile psiFile, @NotNull PsiElement[] elements) {
        InternalProcessor proc = new InternalProcessor(psiFile.getProject(), elements);
        try {
            if (!proc.prepare()) return null;
            proc.prepareVariablesAndName();
        } catch (Exception e) {
            return null;
        }
        return proc.getInputVariables() != null ? proc : null;
    }

    /** Typ-1: sucht exakte strukturelle Duplikate via {@link DuplicatesFinder}. */
    private static List<Match> findExactMatches(
            @NotNull InternalProcessor proc,
            @NotNull PsiElement[] elements,
            @NotNull Set<PsiClass> classesToSearch) {
        ReturnValue returnValue = proc.getOutputVariable() != null
            ? new VariableReturnValue(proc.getOutputVariable()) : null;
        List<PsiVariable> parameters = new ArrayList<>();
        if (proc.getOutputVariables() != null) {
            for (PsiVariable v : proc.getOutputVariables()) {
                if (v != null) parameters.add(v);
            }
        }
        DuplicatesFinder finder = new DuplicatesFinder(
            elements, proc.getInputVariables().copy(), returnValue, parameters
        );
        List<Match> result = new ArrayList<>();
        for (PsiClass cls : classesToSearch) {
            List<Match> found = finder.findDuplicates(cls);
            if (!ContainerUtil.isEmpty(found)) result.addAll(found);
        }
        return result;
    }

   /**
     * Typ-2: sucht parametrisierte Duplikate (unterschiedliche Variablennamen / Literale)
     * via {@link ParametrizedDuplicates}
     */
    private static Set<DuplicateCodeFragment> findParametrizedMatches(
            @NotNull DuplicateCodeFragment anchor,
            @NotNull List<DuplicateCodeFragment> fragments) {
        PsiElement[] elements = anchor.getStatements();
        if (elements == null || elements.length == 0) return Collections.emptySet();
        PsiFile psiFile = anchor.getFile();
        if (psiFile == null) return Collections.emptySet();
        InternalProcessor proc = buildProcessor(psiFile, elements);
        if (proc == null) return Collections.emptySet();
        try {
            ParametrizedDuplicates pd = ParametrizedDuplicates.findDuplicates(proc, MatchType.PARAMETRIZED, null);
            if (pd != null && !ContainerUtil.isEmpty(pd.getDuplicates())) {
                return mapMatchesToFragments(pd.getDuplicates(), fragments, anchor);
            }
        } catch (Exception e) {
            LOG.warn("ParametrizedDuplicates search failed: " + e.getMessage());
        }
        return Collections.emptySet();
    }

    /**
     * Bildet Match-Treffer via TextRange-Offsets auf Fragmente ab.
     */
    private static Set<DuplicateCodeFragment> mapMatchesToFragments(
            @NotNull List<Match> allMatches,
            @NotNull List<DuplicateCodeFragment> fragments,
            @NotNull DuplicateCodeFragment anchor) {
        Set<DuplicateCodeFragment> result = new HashSet<>();
        for (Match match : allMatches) {
            PsiElement matchStart = match.getMatchStart();
            PsiElement matchEnd   = match.getMatchEnd();
            if (matchStart == null || matchEnd == null) continue;
            int matchStartOffset = matchStart.getTextRange().getStartOffset();
            int matchEndOffset   = matchEnd.getTextRange().getEndOffset();
            for (DuplicateCodeFragment f : fragments) {
                if (f == anchor) continue;
                PsiElement[] fStmts = f.getStatements();
                if (fStmts == null || fStmts.length == 0) continue;
                int fStart = fStmts[0].getTextRange().getStartOffset();
                int fEnd   = fStmts[fStmts.length - 1].getTextRange().getEndOffset();
                if (matchStartOffset == fStart && matchEndOffset == fEnd) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    /**
     * Findet welche Fragmente strukturell mit dem Anker übereinstimmen (via DuplicatesFinder).
     */
    private static Set<DuplicateCodeFragment> findMatchingFragments(
            @NotNull DuplicateCodeFragment anchor,
            @NotNull List<DuplicateCodeFragment> fragments) {
        PsiElement[] elements = anchor.getStatements();
        if (elements == null || elements.length == 0) return Collections.emptySet();

        PsiFile psiFile = anchor.getFile();
        if (psiFile == null) return Collections.emptySet();

        Set<PsiClass> classesToSearch = collectClassesToSearch(anchor, fragments);
        if (classesToSearch.isEmpty()) return Collections.emptySet();

        InternalProcessor proc = buildProcessor(psiFile, elements);
        if (proc == null) return Collections.emptySet();

        List<Match> allMatches = new ArrayList<>(findExactMatches(proc, elements, classesToSearch));
        return mapMatchesToFragments(allMatches, fragments, anchor);
    }

    /** Subklasse um geschützte Felder von ExtractMethodProcessor zugänglich zu machen. */
    private static class InternalProcessor extends ExtractMethodProcessor {
        InternalProcessor(@NotNull Project project, PsiElement[] elements) {
            super(project, null, elements, null, "extracted", "extracted", null);
        }
        @Nullable public com.intellij.refactoring.extractMethod.InputVariables getInputVariables() { return myInputVariables; }
        @Nullable public PsiVariable   getOutputVariable()  { return myOutputVariable; }
        @Nullable public PsiVariable[] getOutputVariables() { return myOutputVariables; }
    }
}
