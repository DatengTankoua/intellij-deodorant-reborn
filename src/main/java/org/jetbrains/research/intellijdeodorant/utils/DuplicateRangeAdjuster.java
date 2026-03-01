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
        
        @NotNull public PsiStatement[] getStatements() { return statements; }
        @NotNull public PsiElement getElement() { return statements.length > 0 ? statements[0] : null; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        @NotNull public String getCode() { return code; }
    }
    
    /**
     * Passt PMD-Duplikat-Bereich an refaktorierbare PSI-Strukturen an.
     * Findet relevante Methode, extrahiert Statements ohne Signatur/Klammern.
     * 
     * @param psiFile PSI-Datei
     * @param document Zugehöriges Document
     * @param startLine Start-Zeile (1-basiert)
     * @param endLine End-Zeile (1-basiert, inklusiv)
     * @return AdjustedRange oder null
     */
    @Nullable
    public static AdjustedRange adjustRangeWithLines(@NotNull PsiFile psiFile,
                                                      @NotNull Document document,
                                                      int startLine,
                                                      int endLine) {
        try {
            int startOffset = document.getLineStartOffset(startLine - 1);
            int endOffset = document.getLineEndOffset(endLine - 1);
            
            PsiElement startElement = psiFile.findElementAt(startOffset);
            PsiElement endElement = psiFile.findElementAt(endOffset);
            
            if (startElement == null || endElement == null) return null;
            
            // Finde relevante Methode (einzelne oder dominante)
            PsiMethod targetMethod = findRelevantMethod(psiFile, startOffset, endOffset);
            if (targetMethod == null) return null;
            
            // Extrahiere Statements
            return extractStatementsFromMethod(psiFile, document, targetMethod, startOffset, endOffset);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Findet relevante Methode: einzelne betroffene oder dominante Methode.
     */
    @Nullable
    private static PsiMethod findRelevantMethod(@NotNull PsiFile psiFile, int startOffset, int endOffset) {
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
        
        if (methods.isEmpty()) return null;
        if (methods.size() == 1) return methods.get(0);
        
        // Finde dominante Methode (größte Überschneidung)
        PsiMethod dominant = null;
        int maxOverlap = 0;
        
        for (PsiMethod method : methods) {
            PsiCodeBlock body = method.getBody();
            if (body == null) continue;
            
            TextRange bodyRange = body.getTextRange();
            int overlap = Math.max(0, 
                Math.min(endOffset, bodyRange.getEndOffset()) - 
                Math.max(startOffset, bodyRange.getStartOffset()));
            
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                dominant = method;
            }
        }
        
        return dominant;
    }
    
    /**
     * Extrahiert Statements aus Methode basierend auf PMD-Bereich.
     */
    @Nullable
    private static AdjustedRange extractStatementsFromMethod(@NotNull PsiFile psiFile,
                                                              @NotNull Document document,
                                                              @NotNull PsiMethod method,
                                                              int startOffset,
                                                              int endOffset) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return null;
        
        // Begrenze auf Method-Body
        TextRange bodyRange = body.getTextRange();
        int adjustedStart = Math.max(startOffset, bodyRange.getStartOffset());
        int adjustedEnd = Math.min(endOffset, bodyRange.getEndOffset());
        
        // Filtere Statements im PMD-Bereich
        List<PsiStatement> statementsInRange = new ArrayList<>();
        for (PsiStatement stmt : body.getStatements()) {
            TextRange stmtRange = stmt.getTextRange();
            if (stmtRange.getEndOffset() > adjustedStart && stmtRange.getStartOffset() < adjustedEnd) {
                statementsInRange.add(stmt);
            }
        }
        
        if (statementsInRange.isEmpty()) return null;
        
        // Berechne finalen Bereich
        PsiStatement first = statementsInRange.get(0);
        PsiStatement last = statementsInRange.get(statementsInRange.size() - 1);
        
        int resultStartOffset = first.getTextRange().getStartOffset();
        int resultEndOffset = last.getTextRange().getEndOffset();
        
        int resultStartLine = document.getLineNumber(resultStartOffset) + 1;
        int resultEndLine = document.getLineNumber(resultEndOffset) + 1;
        
        String code = psiFile.getText().substring(resultStartOffset, resultEndOffset);
        
        // Konvertiere zu Array
        PsiStatement[] statementsArray = statementsInRange.toArray(new PsiStatement[0]);
        
        return new AdjustedRange(statementsArray, resultStartLine, resultEndLine, code);
    }
}