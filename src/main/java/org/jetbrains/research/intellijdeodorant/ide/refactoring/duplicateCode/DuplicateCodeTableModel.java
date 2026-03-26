package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

import com.intellij.openapi.application.ReadAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeFragment;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Table Model für Duplicate Code Groups.
 * 
 * Spalten:
 * 1. Occurrences: Anzahl der Duplikate
 * 2. Tokens: Anzahl der Tokens
 * 3. Lines: Durchschnittliche Zeilen
 * 4. Severity: Schwere-Score (Tokens × Occurrences)
 * 5. Type: Cross-File oder Within-File
 * 6. Locations: Liste der Fundorte
 * 
 * Threading: Alle PSI-Zugriffe erfolgen in ReadAction!
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class DuplicateCodeTableModel extends AbstractTableModel {
    
    private static final String[] COLUMN_NAMES = {
        "Occurrences",
        "Tokens",
        "Avg Lines",
        "Severity",
        "Type",
        "Locations"
    };
    
    private final List<DuplicateCodeGroup> groups;
    
    /**
     * Erstellt ein neues DuplicateCodeTableModel.
     */
    public DuplicateCodeTableModel() {
        this.groups = new ArrayList<>();
    }
    
    /**
     * Setzt die Duplicate Code Groups.
     * WICHTIG: Wird im EDT aufgerufen!
     */
    public void setGroups(@NotNull List<DuplicateCodeGroup> groups) {
        this.groups.clear();
        this.groups.addAll(groups);
        
        // Sortiere nach Severity (höchste zuerst)
        this.groups.sort(Comparator.comparingInt(DuplicateCodeGroup::getSeverity).reversed());
        
        fireTableDataChanged();
    }
    
    /**
     * Gibt eine Gruppe an Index zurück.
     */
    @NotNull
    public DuplicateCodeGroup getGroup(int index) {
        return groups.get(index);
    }

    /**
     * Gibt alle Gruppen zurück.
     */
    @NotNull
    public List<DuplicateCodeGroup> getGroups() {
        return java.util.Collections.unmodifiableList(groups);
    }
    
    /**
     * Entfernt eine Gruppe.
     */
    public void removeGroup(int index) {
        groups.remove(index);
        fireTableRowsDeleted(index, index);
    }
    
    /**
     * Leert das Model.
     */
    public void clear() {
        int size = groups.size();
        groups.clear();
        if (size > 0) {
            fireTableRowsDeleted(0, size - 1);
        }
    }
    
    @Override
    public int getRowCount() {
        return groups.size();
    }
    
    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:  // Occurrences
            case 1:  // Tokens
            case 3:  // Severity
                return Integer.class;
            case 2:  // Avg Lines
                return Double.class;
            default:
                return String.class;
        }
    }
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DuplicateCodeGroup group = groups.get(rowIndex);
        
        // Alle PSI-Zugriffe in ReadAction!
        return ReadAction.compute(() -> {
            switch (columnIndex) {
                case 0:  // Occurrences
                    return group.getOccurrences();
                    
                case 1:  // Tokens
                    return group.getFirstFragment().getTokens();
                    
                case 2:  // Avg Lines
                    return Math.round(group.getAverageLines() * 10) / 10.0;
                    
                case 3:  // Severity
                    return group.getSeverity();
                    
                case 4:  // Type
                    return group.isCrossFile() ? "Cross-File" : "Within-File";
                    
                case 5:  // Locations
                    return formatLocations(group);
                    
                default:
                    return "";
            }
        });
    }
    
    /**
     * Formatiert die Locations für Anzeige.
     */
    @NotNull
    private String formatLocations(@NotNull DuplicateCodeGroup group) {
        List<DuplicateCodeFragment> fragments = group.getFragments();
        
        if (fragments.isEmpty()) {
            return "";
        }
        
        if (fragments.size() == 1) {
            return fragments.get(0).getClassQualifiedLocationString();
        }
        
        // Bei Cross-File: Zeige alle Klassennamen
        if (group.isCrossFile()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fragments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(fragments.get(i).getClassName());
            }
            return sb.toString();
        }
        
        // Bei Within-File: Zeige ersten + Anzahl weitere
        StringBuilder sb = new StringBuilder();
        sb.append(fragments.get(0).getClassQualifiedLocationString());
        
        if (fragments.size() > 1) {
            sb.append(" (+ ").append(fragments.size() - 1).append(" more)");
        }
        
        return sb.toString();
    }
    
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
