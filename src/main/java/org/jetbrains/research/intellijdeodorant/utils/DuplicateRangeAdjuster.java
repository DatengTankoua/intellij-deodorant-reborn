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
            List<PsiCodeBlock> innerBlocks = getInnerBlocks(stmt);

            if (crossesBoundary && !innerBlocks.isEmpty()) {
                // Flush bisher gesammelte Statements als eigenen Range
                AdjustedRange flushed = buildRange(psiFile, document, accumulated);
                if (flushed != null) result.add(flushed);
                accumulated.clear();
                // Abstieg in alle inneren Blöcke
                for (PsiCodeBlock inner : innerBlocks) {
                    result.addAll(extractRangesFromBlock(psiFile, document, inner, startOffset, endOffset));
                }
            } else if (!crossesBoundary) {
                accumulated.add(stmt);
            }
            // crossesBoundary && innerBlocks.isEmpty(): kein Block-Rumpf vorhanden → überspringen
        }

        AdjustedRange flushed = buildRange(psiFile, document, accumulated);
        if (flushed != null) result.add(flushed);
        return result;
    }

    /**
     * Gibt den inneren PsiCodeBlock eines Statements zurück, falls vorhanden.
     */
    @Nullable
    private static List<PsiCodeBlock> getInnerBlocks(@NotNull PsiStatement stmt) {
        List<PsiCodeBlock> blocks = new ArrayList<>();
        try {
            // { ... } – Block ist schon ein Block
            if (stmt instanceof PsiBlockStatement) {
                blocks.add(((PsiBlockStatement) stmt).getCodeBlock());
                return blocks;
            }
            // try / catch / finally
            if (stmt instanceof PsiTryStatement) {
                PsiTryStatement tryStmt = (PsiTryStatement) stmt;
                PsiCodeBlock tryBlock = tryStmt.getTryBlock();
                if (tryBlock != null) blocks.add(tryBlock);
                for (PsiCodeBlock cb : tryStmt.getCatchBlocks()) blocks.add(cb);
                PsiCodeBlock finallyBlock = tryStmt.getFinallyBlock();
                if (finallyBlock != null) blocks.add(finallyBlock);
                return blocks;
            }
            // if / else
            if (stmt instanceof PsiIfStatement) {
                PsiIfStatement ifStmt = (PsiIfStatement) stmt;
                addBlockBody(ifStmt.getThenBranch(), blocks);
                addBlockBody(ifStmt.getElseBranch(), blocks);
                return blocks;
            }
            // Schleifen
            PsiStatement loopBody = null;
            if (stmt instanceof PsiForStatement)     loopBody = ((PsiForStatement) stmt).getBody();
            if (stmt instanceof PsiForeachStatement) loopBody = ((PsiForeachStatement) stmt).getBody();
            if (stmt instanceof PsiWhileStatement)   loopBody = ((PsiWhileStatement) stmt).getBody();
            if (stmt instanceof PsiDoWhileStatement) loopBody = ((PsiDoWhileStatement) stmt).getBody();
            addBlockBody(loopBody, blocks);
        } catch (Exception ignored) {}
        return blocks;
    }

    /** Fügt den PsiCodeBlock des Statements zur Liste hinzu, falls es ein PsiBlockStatement ist. */
    private static void addBlockBody(@Nullable PsiStatement body, @NotNull List<PsiCodeBlock> blocks) {
        if (body instanceof PsiBlockStatement) {
            blocks.add(((PsiBlockStatement) body).getCodeBlock());
        }
    }

    /**
     * Zählt die Tokens in einem Array von Statements.
     */
    public static int countTokens(@NotNull PsiStatement[] statements) {
        int[] count = {0};
        for (PsiStatement stmt : statements) {
            stmt.accept(new JavaRecursiveElementVisitor() {
                
                @Override
                public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                    // Annotations werden nicht als Tokens gezählt
                }

                @Override
                public void visitComment(@NotNull PsiComment comment) {
                    // Leer lassen -> Verhindert das Zählen interner JavaDoc-Tokens
                }

                @Override
                public void visitElement(@NotNull PsiElement element) {
                    super.visitElement(element);

                    if (element instanceof PsiJavaToken) {
                        
                        if (element.getTextLength() > 0 ) {
                            count[0]++;
                        }
                    }
                }
            });
        }
        return count[0];
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