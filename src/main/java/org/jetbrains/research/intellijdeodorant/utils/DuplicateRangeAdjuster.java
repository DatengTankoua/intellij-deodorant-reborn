package org.jetbrains.research.intellijdeodorant.utils;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Passt PMD-basierte Duplikat-Bereiche an valide PSI-Strukturen an.
 * Verhindert Mergen benachbarter Methoden, respektiert Methoden-Grenzen,
 * erweitert nur auf vollständige Statements innerhalb EINER Methode.
 * 
 * @author IntelliJDeodorant Team
 */
public class DuplicateRangeAdjuster {
    
    /**
     * Wrapper für angepasste Bereiche mit aktualisierten Zeilen.
     * Speichert ALLE Statements im Bereich, nicht nur das erste.
     */
    public static class AdjustedRange {
        public final PsiStatement[] statements;
        public final int startLine;
        public final int endLine;
        public final String code;
        
        public AdjustedRange(@NotNull PsiStatement[] statements, int startLine, int endLine, @NotNull String code) {
            this.statements = statements;
            this.startLine = startLine;
            this.endLine = endLine;
            this.code = code;
        }
    }
    
   /**
     * Gibt pro überschnittener Methode einen AdjustedRange zurück.
     * Berührt ein CPD-Fragment zwei Methoden, entstehen zwei Refactoring-Kandidaten.
     *
     * @param psiFile   PSI-Datei
     * @param document  Zugehöriges Document
     * @param startLine Start-Zeile
     * @param endLine   End-Zeile
     * @return Liste von AdjustedRanges
     */
    @NotNull
    public static List<AdjustedRange> adjustRangeWithLines(@NotNull PsiFile psiFile,
                                                            @NotNull Document document,
                                                            int startLine,
                                                            int endLine) {
        List<AdjustedRange> result = new ArrayList<>();
        try {
            int startOffset = document.getLineStartOffset(startLine - 1);
            int endOffset   = document.getLineEndOffset(endLine - 1);
            for (PsiMethod method : findOverlappingMethods(psiFile, startOffset, endOffset)) {
                PsiCodeBlock body = method.getBody();
                if (body != null) {
                    result.addAll(extractRangesFromBlock(psiFile, document, body, startOffset, endOffset));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
    
    /** Sammelt alle Methoden, die den [startOffset, endOffset]-Bereich überschneiden. */
    @NotNull
    private static List<PsiMethod> findOverlappingMethods(@NotNull PsiFile psiFile,
                                                           int startOffset, int endOffset) {
        List<PsiMethod> methods = new ArrayList<>();
        psiFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                TextRange range = method.getTextRange();
                if (range.getStartOffset() < endOffset && range.getEndOffset() > startOffset) {
                    methods.add(method);
                }
            }
        });
        return methods;
    }
    
    /**
     * Extrahiert AdjustedRanges aus einem PsiCodeBlock rekursiv.
     *
     * Regel: Überschreitet ein Statement die CPD-Grenze UND hat es einen inneren Block
     * (z.B. Schleifen-Rumpf), wird in diesen Block abgestiegen statt das ganze Statement
     * aufzunehmen. So entsteht pro Schleife/Block ein eigener AdjustedRange.
     */
    @NotNull
    private static List<AdjustedRange> extractRangesFromBlock(@NotNull PsiFile psiFile,
                                                               @NotNull Document document,
                                                               @NotNull PsiCodeBlock block,
                                                               int startOffset,
                                                               int endOffset) {
        TextRange blockRange = block.getTextRange();
        int adjStart = Math.max(startOffset, blockRange.getStartOffset());
        int adjEnd   = Math.min(endOffset,   blockRange.getEndOffset());

        List<AdjustedRange> result      = new ArrayList<>();
        List<PsiStatement>  accumulated = new ArrayList<>();

        for (PsiStatement stmt : block.getStatements()) {
            TextRange sr = stmt.getTextRange();
            if (sr.getEndOffset() <= adjStart || sr.getStartOffset() >= adjEnd) continue;

            boolean crossesBoundary = sr.getStartOffset() < adjStart || sr.getEndOffset() > adjEnd;
            PsiCodeBlock inner = getInnerBlock(stmt);

            if (crossesBoundary && inner != null) {
                // Flush bisher gesammelte Statements als eigenen Range
                AdjustedRange flushed = buildRange(psiFile, document, accumulated);
                if (flushed != null) result.add(flushed);
                accumulated.clear();
                // Abstieg in den inneren Block
                result.addAll(extractRangesFromBlock(psiFile, document, inner, startOffset, endOffset));
            } else {
                accumulated.add(stmt);
            }
        }

        AdjustedRange flushed = buildRange(psiFile, document, accumulated);
        if (flushed != null) result.add(flushed);
        return result;
    }

    /**
     * Gibt den inneren PsiCodeBlock eines Statements zurück, falls vorhanden.
     */
    @Nullable
    private static PsiCodeBlock getInnerBlock(@NotNull PsiStatement stmt) {
        try {
            PsiStatement body = null;
            if (stmt instanceof PsiBlockStatement)   return ((PsiBlockStatement) stmt).getCodeBlock();
            if (stmt instanceof PsiTryStatement)     return ((PsiTryStatement) stmt).getTryBlock();
            if (stmt instanceof PsiForStatement)     body = ((PsiForStatement) stmt).getBody();
            if (stmt instanceof PsiForeachStatement) body = ((PsiForeachStatement) stmt).getBody();
            if (stmt instanceof PsiWhileStatement)   body = ((PsiWhileStatement) stmt).getBody();
            if (stmt instanceof PsiDoWhileStatement) body = ((PsiDoWhileStatement) stmt).getBody();
            if (stmt instanceof PsiIfStatement)      body = ((PsiIfStatement) stmt).getThenBranch();
            if (body instanceof PsiBlockStatement)   return ((PsiBlockStatement) body).getCodeBlock();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Baut einen AdjustedRange aus einer Liste von Statements. */
    @Nullable
    private static AdjustedRange buildRange(@NotNull PsiFile psiFile,
                                             @NotNull Document document,
                                             @NotNull List<PsiStatement> statements) {
        if (statements.isEmpty()) return null;
        PsiStatement first = statements.get(0);
        PsiStatement last  = statements.get(statements.size() - 1);
        int rStart = first.getTextRange().getStartOffset();
        int rEnd   = last.getTextRange().getEndOffset();
        return new AdjustedRange(
            statements.toArray(new PsiStatement[0]),
            document.getLineNumber(rStart) + 1,
            document.getLineNumber(rEnd)   + 1,
            psiFile.getText().substring(rStart, rEnd)
        );
    }
}