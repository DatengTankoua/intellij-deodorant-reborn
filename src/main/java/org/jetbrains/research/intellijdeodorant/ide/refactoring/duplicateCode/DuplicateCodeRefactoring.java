package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

import com.intellij.openapi.application.ReadAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.Refactoring;

/**
 * Repräsentiert ein Duplicate Code Refactoring.
 * 
 * Typen:
 * - EXTRACT_METHOD: Extrahiere duplizierten Code in neue Methode
 * - PULL_UP_METHOD: Verschiebe duplizierte Methode in Superklasse
 * - FORM_TEMPLATE_METHOD: Erstelle Template Method Pattern
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class DuplicateCodeRefactoring implements Refactoring {
    
    /**
     * Refactoring-Strategien für Duplicate Code.
     */
    public enum Strategy {
        /**
         * Extrahiere duplizierten Code in eine neue Methode.
         * Anwendbar: Wenn mehrere Stellen denselben Code enthalten.
         */
        EXTRACT_METHOD("Extract Method"),
        
        /**
         * Verschiebe duplizierte Methode in Superklasse.
         * Anwendbar: Wenn Duplikate in Subklassen derselben Hierarchie vorkommen.
         */
        PULL_UP_METHOD("Pull Up Method"),
        
        /**
         * Erstelle Template Method Pattern.
         * Anwendbar: Wenn Duplikate ähnlich aber nicht identisch sind.
         */
        FORM_TEMPLATE_METHOD("Form Template Method");
        
        private final String displayName;
        
        Strategy(String displayName) {
            this.displayName = displayName;
        }
        
        @NotNull
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final DuplicateCodeGroup group;
    private final Strategy strategy;
    
    /**
     * Erstellt ein neues DuplicateCodeRefactoring.
     * 
     * @param group Duplicate Code Group
     * @param strategy Refactoring-Strategie
     */
    public DuplicateCodeRefactoring(@NotNull DuplicateCodeGroup group,
                                     @NotNull Strategy strategy) {
        this.group = group;
        this.strategy = strategy;
    }
    
    /**
     * Gibt die Duplicate Code Group zurück.
     */
    @NotNull
    public DuplicateCodeGroup getGroup() {
        return group;
    }
    
    /**
     * Gibt die Refactoring-Strategie zurück.
     */
    @NotNull
    public Strategy getStrategy() {
        return strategy;
    }
    
    @NotNull
    @Override
    public String getDescription() {
        return ReadAction.compute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(strategy.getDisplayName()).append(DELIMITER);
            sb.append(group.getOccurrences()).append(" duplicates").append(DELIMITER);
            sb.append(group.getTokens()).append(" tokens").append(DELIMITER);
            
            group.getFragments().forEach(fragment -> {
                sb.append(fragment.getLocationString()).append(DELIMITER);
            });
            
            sb.append("Severity: ").append(group.getSeverity());
            
            return sb.toString();
        });
    }
    
    @NotNull
    @Override
    public String getExportDefaultFilename() {
        return "duplicate-code-refactorings.txt";
    }
    
    /**
     * Prüft ob das Refactoring noch gültig ist.
     */
    public boolean isValid() {
        return ReadAction.compute(() -> 
            group.getOccurrences() >= 2 && 
            group.getTokens() >= 25 &&
            group.getFragments().stream()
                .allMatch(f -> f.getFile().isValid())
        );
    }
}
