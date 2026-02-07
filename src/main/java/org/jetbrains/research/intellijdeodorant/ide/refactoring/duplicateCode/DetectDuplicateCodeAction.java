package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.ide.ui.DuplicateCodePanel;

/**
 * Action zum Erkennen von Duplicate Code mittels PMD.
 * 
 * Diese Action öffnet ein Panel mit einer Tabelle aller Duplikate.
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class DetectDuplicateCodeAction extends AnAction {
    
    private static final Logger LOG = Logger.getInstance(DetectDuplicateCodeAction.class);
    private static final String TOOL_WINDOW_ID = "Duplicate Code";
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        LOG.info("Opening Duplicate Code Detection Panel");
        
        // Tool Window Manager holen
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        
        // Prüfen ob Tool Window bereits existiert
        ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
        
        if (toolWindow == null) {
            // Neues Tool Window erstellen
            toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM);
        }
        
        // Panel erstellen
        AnalysisScope scope = new AnalysisScope(project);
        DuplicateCodePanel panel = new DuplicateCodePanel(project, scope);
        
        // Content erstellen und hinzufügen
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        
        // Alte Contents entfernen
        toolWindow.getContentManager().removeAllContents(true);
        toolWindow.getContentManager().addContent(content);
        
        // Tool Window anzeigen
        toolWindow.show();
    }
}
