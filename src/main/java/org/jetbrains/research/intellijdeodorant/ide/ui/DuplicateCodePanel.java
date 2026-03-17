package org.jetbrains.research.intellijdeodorant.ide.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.EditorHelper;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.messages.MessageBus;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.IntelliJDeodorantBundle;
import org.jetbrains.research.intellijdeodorant.core.distance.ProjectInfo;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeFragment;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;
import org.jetbrains.research.intellijdeodorant.core.duplication.PMDDuplicateCodeDetector;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.DuplicateCodeTableModel;
import org.jetbrains.research.intellijdeodorant.ide.ui.listeners.DoubleClickListener;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UI Panel für die Anzeige von Duplicate Code Detection Ergebnissen.
 * Zeigt alle gefundenen Code-Duplikate in einer Tabelle an.
 */
public class DuplicateCodePanel extends JPanel {
    
    private static final NotificationGroup NOTIFICATION_GROUP =
            new NotificationGroup(IntelliJDeodorantBundle.message("intellijdeodorant"), 
                                NotificationDisplayType.STICKY_BALLOON, true);
    
    private final Project project;
    private final AnalysisScope scope;
    private final DuplicateCodeTableModel tableModel;
    private final JBTable table;
    private final JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
    private final JButton doRefactorButton = new JButton(AllIcons.Actions.RefactoringBulb);
    private final JButton exportButton = new JButton(AllIcons.ToolbarDecorator.Export);
    private JScrollPane scrollPane = new JBScrollPane();
    private final JLabel refreshLabel = new JLabel(
            IntelliJDeodorantBundle.message("press.refresh.to.find.refactoring.opportunities"),
            SwingConstants.CENTER
    );
    private final ScopeChooserCombo scopeChooserCombo;
    private final PMDDuplicateCodeDetector detector = new PMDDuplicateCodeDetector();

    
    public DuplicateCodePanel(@NotNull Project project, @NotNull AnalysisScope scope) {
        this.project = project;
        this.scope = scope;
        this.scopeChooserCombo = new ScopeChooserCombo(project);
        this.tableModel = new DuplicateCodeTableModel();
        
        // Custom JBTable mit Tooltip-Unterstützung für detaillierte Locations
        this.table = new JBTable(tableModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                
                if (row >= 0 && column == 5) {  // Locations-Spalte
                    int modelRow = convertRowIndexToModel(row);
                    DuplicateCodeGroup group = tableModel.getGroup(modelRow);
                    
                    // Zeige alle Locations im Tooltip
                    return ReadAction.compute(() -> {
                        StringBuilder tooltip = new StringBuilder("<html>");
                        tooltip.append("<b>All Locations:</b><br>");
                        
                        for (int i = 0; i < group.getFragments().size(); i++) {
                            DuplicateCodeFragment fragment = group.getFragments().get(i);
                            tooltip.append((i + 1)).append(". ");
                            tooltip.append(fragment.getClassQualifiedLocationString());
                            tooltip.append("<br>");
                        }
                        
                        tooltip.append("</html>");
                        return tooltip.toString();
                    });
                }
                
                return super.getToolTipText(e);
            }
        };
        
        refreshLabel.setForeground(JBColor.GRAY);
        setLayout(new BorderLayout());
        
        setupTable();
        setupToolbar();
        setupListeners();
        registerPsiModificationListener();
        
        add(refreshLabel, BorderLayout.CENTER);
    }
    
    private void setupTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        
        // Spaltenbreiten konfigurieren
        TableColumn occurrencesColumn = table.getColumnModel().getColumn(0);
        occurrencesColumn.setPreferredWidth(100);
        occurrencesColumn.setMaxWidth(120);
        
        TableColumn tokensColumn = table.getColumnModel().getColumn(1);
        tokensColumn.setPreferredWidth(100);
        tokensColumn.setMaxWidth(120);
        
        TableColumn linesColumn = table.getColumnModel().getColumn(2);
        linesColumn.setPreferredWidth(100);
        linesColumn.setMaxWidth(120);
        
        TableColumn severityColumn = table.getColumnModel().getColumn(3);
        severityColumn.setPreferredWidth(100);
        severityColumn.setMaxWidth(120);
        
        // Severity mit Farben rendern
        severityColumn.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                         boolean isSelected, boolean hasFocus,
                                                         int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, 
                                                                 hasFocus, row, column);
                if (value instanceof Integer) {
                    int severity = (Integer) value;
                    if (severity > 1000) {
                        c.setForeground(JBColor.RED);
                    } else if (severity > 500) {
                        c.setForeground(JBColor.ORANGE);
                    } else {
                        c.setForeground(JBColor.YELLOW.darker());
                    }
                }
                return c;
            }
        });
        
        // Schnellsuche aktivieren
        new TableSpeedSearch(table);
        
        scrollPane = ScrollPaneFactory.createScrollPane(table);
    }
    
    private void setupToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Scope Chooser - ermöglicht Auswahl von All Files, Current File, Opened Files
        scopeChooserCombo.setToolTipText("Searching Scope");
        toolbar.add(scopeChooserCombo);
        
        refreshButton.setToolTipText("Detect duplicate code");
        refreshButton.addActionListener(e -> detectDuplicates());
        toolbar.add(refreshButton);
        
        doRefactorButton.setToolTipText("Extract duplicated code to method");
        doRefactorButton.setEnabled(false);
        doRefactorButton.addActionListener(e -> extractMethod());
        toolbar.add(doRefactorButton);
        
        exportButton.setToolTipText("Export results");
        exportButton.setEnabled(false);
        toolbar.add(exportButton);
        
        add(toolbar, BorderLayout.NORTH);
    }
    
    /**
     * Registriert einen PSI-Änderungs-Listener, der das Panel bei Code-Änderungen zurücksetzt.
     */
    private void registerPsiModificationListener() {
        MessageBus projectMessageBus = project.getMessageBus();
        projectMessageBus.connect().subscribe(PsiModificationTracker.TOPIC, 
            () -> ApplicationManager.getApplication().invokeLater(this::showRefreshingProposal));
    }
    
    /**
     * Zeigt die "Refresh"-Nachricht an und leert die Tabelle.
     * Wird aufgerufen wenn der Code geändert wurde.
     */
    private void showRefreshingProposal() {
        tableModel.clear();
        remove(scrollPane);
        add(refreshLabel, BorderLayout.CENTER);
        revalidate();
        repaint();
        refreshButton.setEnabled(true);
        doRefactorButton.setEnabled(false);
        exportButton.setEnabled(false);
    }
    
    private void setupListeners() {
        // Selection Listener - aktiviert Refactor-Button und highlightet Code
        table.getSelectionModel().addListSelectionListener(this::onSelectionChanged);
        
        // Doppelklick auf Zeile markiert alle Duplikate der Gruppe
        table.addMouseListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(InputEvent event) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(selectedRow);
                    DuplicateCodeGroup group = tableModel.getGroup(modelRow);
                    if (group != null && !group.getFragments().isEmpty()) {
                        // Markiere ALLE Duplikate dieser Gruppe grün
                        highlightAllDuplicates(group);
                        // Öffne erste Fragment-Location
                        group.getFragments().get(0).navigateToSource();
                    }
                }
            }
        });
    }
    
    private void onSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            DuplicateCodeGroup group = tableModel.getGroup(modelRow);
            
            // Aktiviere Refactor-Button
            doRefactorButton.setEnabled(true);
            
            // Highlighte ersten Duplikat im Editor
            if (group != null && !group.getFragments().isEmpty()) {
                highlightDuplicateCode(group.getFragments().get(0));
            }
        } else {
            doRefactorButton.setEnabled(false);
            removeHighlighters();
        }
    }
    
    private void highlightAllDuplicates(DuplicateCodeGroup group) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Highlighting duplicates...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
            }
            
            @Override
            public void onSuccess() {
                ReadAction.run(() -> {
                    // Entferne alle alten Highlighter in allen offenen Editoren
                    for (com.intellij.openapi.fileEditor.FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
                        if (fileEditor instanceof TextEditor) {
                            ((TextEditor) fileEditor).getEditor().getMarkupModel().removeAllHighlighters();
                        }
                    }
                    
                    TextAttributes attributes = EditorColorsManager.getInstance()
                            .getGlobalScheme()
                            .getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
                    
                    // Markiere ALLE Fragmente der Gruppe
                    for (DuplicateCodeFragment fragment : group.getFragments()) {
                        PsiFile psiFile = fragment.getFile();
                        if (psiFile == null || !psiFile.isValid()) {
                            continue;
                        }
                        
                        // Öffne Datei im Editor (ohne Fokus zu ändern)
                        Editor editor = EditorHelper.openInEditor(psiFile);
                        if (editor == null) {
                            continue;
                        }
                        
                        // Berechne präzise Offsets basierend auf Zeilen
                        com.intellij.openapi.editor.Document document = editor.getDocument();
                        int startLine = fragment.getStartLine();
                        int endLine = fragment.getEndLine();
                        
                        if (startLine > 0 && endLine > 0 && 
                            startLine <= document.getLineCount() && 
                            endLine <= document.getLineCount()) {
                            
                            int startOffset = document.getLineStartOffset(startLine - 1);
                            int endOffset = document.getLineEndOffset(endLine - 1);
                            
                            // Füge Highlighter hinzu (grün)
                            editor.getMarkupModel().addRangeHighlighter(
                                    startOffset,
                                    endOffset,
                                    HighlighterLayer.SELECTION,
                                    attributes,
                                    HighlighterTargetArea.EXACT_RANGE
                            );
                        }
                    }
                });
            }
        };
        
        task.queue();
    }
    
    private void highlightDuplicateCode(DuplicateCodeFragment fragment) {
        // Erstelle temporäre Gruppe mit nur diesem Fragment für Single-Highlighting
        DuplicateCodeGroup tempGroup = new DuplicateCodeGroup(fragment.getTokens());
        tempGroup.addFragment(fragment);
        highlightAllDuplicates(tempGroup);
    }
    
    private void removeHighlighters() {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            editor.getMarkupModel().removeAllHighlighters();
        }
    }
    
    private void extractMethod() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        
        int modelRow = table.convertRowIndexToModel(selectedRow);
        DuplicateCodeGroup group = tableModel.getGroup(modelRow);
        
        if (group == null || group.getFragments().isEmpty()) {
            return;
        }
        
        // Verwende den neuen DuplicateCodeRefactoringHandler
        org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.DuplicateCodeRefactoringHandler handler =
            new org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.DuplicateCodeRefactoringHandler(project);
        
        handler.performRefactoring(group);
    }
    
    private void detectDuplicates() {
        // Scope VOR dem Background-Task abrufen (benötigt ReadAction)
        // getScope() ruft intern PSI-Methoden auf, die nur im Read-Action erlaubt sind
        final AnalysisScope currentScope = scopeChooserCombo.getScope();
        
        if (currentScope == null) {
            return;
        }
        
        refreshButton.setEnabled(false);
        tableModel.clear();
        
        remove(refreshLabel);
        add(scrollPane, BorderLayout.CENTER);
        revalidate();
        repaint();
        
        Task.Backgroundable task = new Task.Backgroundable(project, "Detecting Duplicate Code...", true) {
            private Set<DuplicateCodeGroup> groups;
            
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Initializing duplicate code detection...");
                
                try {
                    // ProjectInfo erstellen
                    ProjectInfo projectInfo = ReadAction.compute(() -> new ProjectInfo(currentScope, false));
                    
                    // Duplikate detektieren
                    groups = detector.detectDuplicates(projectInfo, indicator);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onSuccess() {
                if (groups != null && !groups.isEmpty()) {
                    // Sortiere nach Severity (absteigend)
                    List<DuplicateCodeGroup> sortedGroups = groups.stream()
                            .sorted(Comparator.comparingInt(DuplicateCodeGroup::getSeverity).reversed())
                            .collect(Collectors.toList());
                    
                    tableModel.setGroups(sortedGroups);
                    exportButton.setEnabled(true);
                    
                    String message = String.format("Found %d duplicate code groups", groups.size());
                    showInfoMessage(message);
                } else {
                    showInfoMessage("No duplicate code found");
                }
                refreshButton.setEnabled(true);
            }
            
            @Override
            public void onCancel() {
                showInfoMessage("Detection cancelled");
                refreshButton.setEnabled(true);
            }
            
            @Override
            public void onThrowable(@NotNull Throwable error) {
                showErrorMessage("Error during detection: " + error.getMessage());
                refreshButton.setEnabled(true);
            }
        };
        
        task.queue();
    }
    
    private void showInfoMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Duplicate Code Detection", 
                                     JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", 
                                     JOptionPane.ERROR_MESSAGE);
    }
}
