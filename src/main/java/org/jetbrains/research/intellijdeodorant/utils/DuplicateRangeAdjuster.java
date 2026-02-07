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
 * 
 * INSPIRIERT VON JDeodorant:
 * - Verwendet strukturelle Validierung statt reiner Token-Matching
 * - Verhindert das Mergen benachbarter Methoden (Hauptproblem)
 * - Respektiert Methoden-Grenzen strikt
 * - Erweitert nur auf vollständige Statements innerhalb EINER Methode
 * 
 * WICHTIGE REGEL:
 * - Wenn PMD-Bereich MEHRERE Methoden überschneidet → KEINE Anpassung
 * - Wenn PMD-Bereich innerhalb EINER Methode → Statement-basierte Anpassung
 * 
 * @author IntelliJDeodorant Team
 * @see <a href="https://github.com/tsantalis/JDeodorant">JDeodorant CloneInstanceMapper</a>
 */
public class DuplicateRangeAdjuster {
    
    /**
     * Passt einen PMD-Duplikat-Bereich an refaktorierbare PSI-Strukturen an.
     */
    @Nullable
    public static PsiElement adjustRange(@NotNull PsiFile psiFile,
                                         @NotNull Document document,
                                         int startLine,
                                         int endLine) {
        try {
            // Konvertiere Zeilen zu Offsets (0-basiert für PSI)
            int startOffset = document.getLineStartOffset(startLine - 1);
            int endOffset = document.getLineEndOffset(endLine - 1);
            
            // Finde Elemente an Start und Ende
            PsiElement startElement = psiFile.findElementAt(startOffset);
            PsiElement endElement = psiFile.findElementAt(endOffset);
            
            if (startElement == null || endElement == null) {
                return null;
            }
            
            // Finde alle Methoden, die vom PMD-Bereich berührt werden
            List<PsiMethod> affectedMethods = findAffectedMethods(psiFile, startOffset, endOffset);
            
            // Prüfe ob der Bereich eine vollständige Methode enthält (≥80% Coverage)
            for (PsiMethod method : affectedMethods) {
                double coverage = calculateMethodCoverage(method, startOffset, endOffset);
                if (coverage >= 0.8) {
                    // Vollständige Methode gefunden - verwende nur den Body
                    PsiCodeBlock body = method.getBody();
                    return body != null ? body : method;
                }
            }
            
            // Finde die dominante Methode (größter Anteil am Fragment)
            PsiMethod dominantMethod = findDominantMethod(affectedMethods, startOffset, endOffset);
            
            if (dominantMethod != null) {
                // Fragment auf die dominante Methode beschränken
                PsiCodeBlock methodBody = dominantMethod.getBody();
                if (methodBody != null) {
                    // Begrenze Fragment auf Methoden-Grenzen
                    int methodStart = methodBody.getTextRange().getStartOffset();
                    int methodEnd = methodBody.getTextRange().getEndOffset();
                    
                    int adjustedStart = Math.max(startOffset, methodStart);
                    int adjustedEnd = Math.min(endOffset, methodEnd);
                    
                    // Finde Statement-Grenzen innerhalb der Methode
                    PsiElement adjustedStartElement = psiFile.findElementAt(adjustedStart);
                    PsiElement adjustedEndElement = psiFile.findElementAt(adjustedEnd);
                    
                    if (adjustedStartElement != null && adjustedEndElement != null) {
                        PsiElement result = expandToCompleteStatements(
                            psiFile, adjustedStartElement, adjustedEndElement
                        );
                        
                        // Prüfe auf balanced Braces
                        if (result != null && hasBalancedBraces(result.getText())) {
                            return result;
                        }
                    }
                }
            }
            
            // Fallback: Verwende commonParent ohne Methoden-Expansion
            PsiElement commonParent = PsiTreeUtil.findCommonParent(startElement, endElement);
            return expandToCompleteStatementsOnly(psiFile, startElement, endElement, commonParent);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Findet alle Methoden, die vom gegebenen Offset-Bereich berührt werden.
     */
    @NotNull
    private static List<PsiMethod> findAffectedMethods(@NotNull PsiFile psiFile, 
                                                        int startOffset, 
                                                        int endOffset) {
        List<PsiMethod> methods = new ArrayList<>();
        
        psiFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                
                TextRange methodRange = method.getTextRange();
                // Prüfe auf Überschneidung
                if (methodRange.getStartOffset() < endOffset && 
                    methodRange.getEndOffset() > startOffset) {
                    methods.add(method);
                }
            }
        });
        
        return methods;
    }
    
    /**
     * Findet die dominante Methode (Methode mit größtem Anteil am Fragment).
     */
    @Nullable
    private static PsiMethod findDominantMethod(@NotNull List<PsiMethod> methods,
                                                 int startOffset,
                                                 int endOffset) {
        PsiMethod dominant = null;
        int maxOverlap = 0;
        
        for (PsiMethod method : methods) {
            PsiCodeBlock body = method.getBody();
            if (body == null) continue;
            
            TextRange bodyRange = body.getTextRange();
            int bodyStart = bodyRange.getStartOffset();
            int bodyEnd = bodyRange.getEndOffset();
            
            // Berechne Überschneidung
            int overlapStart = Math.max(startOffset, bodyStart);
            int overlapEnd = Math.min(endOffset, bodyEnd);
            int overlap = Math.max(0, overlapEnd - overlapStart);
            
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                dominant = method;
            }
        }
        
        return dominant;
    }
    
    /**
     * Prüft ob ein Text balanced Braces hat (gleiche Anzahl { und }).
     */
    private static boolean hasBalancedBraces(@NotNull String text) {
        int openCount = 0;
        int closeCount = 0;
        
        for (char c : text.toCharArray()) {
            if (c == '{') openCount++;
            else if (c == '}') closeCount++;
        }
        
        return openCount == closeCount;
    }
    
    /**
     * Erweitert auf vollständige Statements OHNE Methoden-Expansion.
     */
    @NotNull
    private static PsiElement expandToCompleteStatementsOnly(@NotNull PsiFile psiFile,
                                                              @NotNull PsiElement startElement,
                                                              @NotNull PsiElement endElement,
                                                              @Nullable PsiElement commonParent) {
        if (commonParent == null) {
            return startElement;
        }
        
        // Finde vollständige Statements
        PsiElement firstStatement = findFirstCompleteStatement(startElement);
        PsiElement lastStatement = findLastCompleteStatement(endElement);
        
        if (firstStatement == null || lastStatement == null) {
            return commonParent;
        }
        
        // Wenn es dasselbe Statement ist
        if (firstStatement == lastStatement) {
            return firstStatement;
        }
        
        // Finde den Container für beide Statements
        PsiElement container = PsiTreeUtil.findCommonParent(firstStatement, lastStatement);
        
        //Wenn Container eine Methode ist, gebe NICHT die Methode zurück
        // sondern den CodeBlock innerhalb
        if (container instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) container;
            PsiCodeBlock body = method.getBody();
            return body != null ? body : container;
        }
        
        // Wenn Container ein Block ist, ist das OK
        if (container instanceof PsiCodeBlock) {
            return container;
        }
        
        return container != null ? container : firstStatement;
    }
    
    /**
     * Berechnet wie viel Prozent einer Methode vom gegebenen Bereich abgedeckt wird.
     */
    private static double calculateMethodCoverage(@NotNull PsiMethod method,
                                                   int startOffset,
                                                   int endOffset) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return 0.0;
        }
        
        // Berechne die Länge des Methoden-Bodies (ohne Signatur)
        int bodyStart = body.getTextRange().getStartOffset();
        int bodyEnd = body.getTextRange().getEndOffset();
        int bodyLength = bodyEnd - bodyStart;
        
        if (bodyLength == 0) {
            return 0.0;
        }
        
        // Berechne Überschneidung zwischen Bereich und Body
        int overlapStart = Math.max(startOffset, bodyStart);
        int overlapEnd = Math.min(endOffset, bodyEnd);
        int overlapLength = Math.max(0, overlapEnd - overlapStart);
        
        return (double) overlapLength / bodyLength;
    }
    
    /**
     * Erweitert den Bereich auf vollständige Statements.
     */
    @NotNull
    private static PsiElement expandToCompleteStatements(@NotNull PsiFile psiFile,
                                                          @NotNull PsiElement startElement,
                                                          @NotNull PsiElement endElement) {
        // Finde das erste vollständige Statement am Start
        PsiElement firstStatement = findFirstCompleteStatement(startElement);
        
        // Finde das letzte vollständige Statement am Ende
        PsiElement lastStatement = findLastCompleteStatement(endElement);
        
        if (firstStatement == null || lastStatement == null) {
            // Fallback: Gemeinsames Parent
            PsiElement commonParent = PsiTreeUtil.findCommonParent(startElement, endElement);
            return commonParent != null ? commonParent : startElement;
        }
        
        // Wenn Start und Ende im gleichen Statement sind
        if (firstStatement == lastStatement) {
            return firstStatement;
        }
        
        // Finde gemeinsames Parent, das beide Statements enthält
        PsiElement commonParent = PsiTreeUtil.findCommonParent(firstStatement, lastStatement);
        
        // Wenn Parent ein Block ist, verwende den Block
        if (commonParent instanceof PsiCodeBlock) {
            return commonParent;
        }
        
        // WICHTIG: NIE eine ganze Methode oder Klasse zurückgeben
        // Wenn Parent eine Methode ist, gebe den CodeBlock zurück
        if (commonParent instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) commonParent;
            PsiCodeBlock body = method.getBody();
            return body != null ? body : commonParent;
        }
        
        // Wenn Parent eine Klasse ist, versuche einen kleineren Bereich zu finden
        if (commonParent instanceof PsiClass) {
            return firstStatement;
        }
        
        return commonParent != null ? commonParent : firstStatement;
    }
    
    /**
     * Findet das erste vollständige Statement ab einem Element.
     */
    @Nullable
    private static PsiElement findFirstCompleteStatement(@NotNull PsiElement element) {
        // Gehe nach oben bis wir ein Statement finden
        PsiElement current = element;
        while (current != null) {
            if (isCompleteStatement(current)) {
                return current;
            }
            
            // Prüfe auch Geschwister-Elemente
            PsiElement parent = current.getParent();
            if (parent != null) {
                PsiElement[] children = parent.getChildren();
                for (PsiElement child : children) {
                    if (child.getTextRange().getStartOffset() >= element.getTextRange().getStartOffset()
                            && isCompleteStatement(child)) {
                        return child;
                    }
                }
            }
            
            current = current.getParent();
        }
        
        return null;
    }
    
    /**
     * Findet das letzte vollständige Statement bis zu einem Element.
     */
    @Nullable
    private static PsiElement findLastCompleteStatement(@NotNull PsiElement element) {
        // Gehe nach oben bis wir ein Statement finden
        PsiElement current = element;
        while (current != null) {
            if (isCompleteStatement(current)) {
                return current;
            }
            
            // Prüfe auch Geschwister-Elemente (rückwärts)
            PsiElement parent = current.getParent();
            if (parent != null) {
                PsiElement[] children = parent.getChildren();
                for (int i = children.length - 1; i >= 0; i--) {
                    PsiElement child = children[i];
                    if (child.getTextRange().getEndOffset() <= element.getTextRange().getEndOffset()
                            && isCompleteStatement(child)) {
                        return child;
                    }
                }
            }
            
            current = current.getParent();
        }
        
        return null;
    }
    
    /**
     * Prüft ob ein Element ein vollständiges Statement ist.
     */
    private static boolean isCompleteStatement(@NotNull PsiElement element) {
        // Prüfe auf verschiedene Statement-Typen
        if (element instanceof PsiStatement) {
            return true;
        }
        
        if (element instanceof PsiMethod) {
            return true;
        }
        
        if (element instanceof PsiField) {
            return true;
        }
        
        if (element instanceof PsiClass) {
            return true;
        }
        
        if (element instanceof PsiCodeBlock) {
            return true;
        }
        
        return false;
    }
}
