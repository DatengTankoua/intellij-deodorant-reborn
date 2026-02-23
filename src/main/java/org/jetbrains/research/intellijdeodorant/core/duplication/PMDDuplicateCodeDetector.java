package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import net.sourceforge.pmd.cpd.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.core.distance.ProjectInfo;
import org.jetbrains.research.intellijdeodorant.utils.DuplicateRangeAdjuster;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Implementiert Duplicate Code Detection mittels PMD's Copy-Paste Detector (CPD) v7.7.0.
 * 
 * CPD verwendet einen token-basierten Ansatz:
 * 1. Code wird in Tokens zerlegt (Whitespace/Kommentare ignoriert)
 * 2. Token-Sequenzen werden verglichen
 * 3. Duplikate werden identifiziert wenn sie mindestens eine bestimmte Anzahl von Tokens identisch sind
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class PMDDuplicateCodeDetector {
    
    private static final Logger LOG = Logger.getInstance(PMDDuplicateCodeDetector.class);
    
    // Konfigurations-Parameter
    private int minimumTileSize = 60;  // Minimum tokens für Duplikat
    private boolean ignoreAnnotations = true;
    private boolean ignoreIdentifiers = false;
    private boolean ignoreLiterals = false;
    
    /**
     * Erstellt einen neuen PMDDuplicateCodeDetector mit Standard-Konfiguration.
     */
    public PMDDuplicateCodeDetector() {
        // Standard-Werte
    }
    
    public void setMinimumTileSize(int minimumTileSize) {
        if (minimumTileSize < 50) {
            LOG.warn("minimumTileSize < 50 kann zu vielen False Positives führen");
        }
        this.minimumTileSize = minimumTileSize;
    }
    
    public void setIgnoreIdentifiers(boolean ignoreIdentifiers) {
        this.ignoreIdentifiers = ignoreIdentifiers;
    }
    
    public void setIgnoreLiterals(boolean ignoreLiterals) {
        this.ignoreLiterals = ignoreLiterals;
    }
    
    @NotNull
    public Set<DuplicateCodeGroup> detectDuplicates(@NotNull ProjectInfo projectInfo,
                                                     @NotNull ProgressIndicator indicator) {
        LOG.info("Starting duplicate code detection with minimumTileSize=" + minimumTileSize);
        
        indicator.setText("Initializing PMD CPD...");
        
        Set<DuplicateCodeGroup> groups = new HashSet<>();
        
        try {
            // CPD konfigurieren
            CPDConfiguration config = createCPDConfiguration();
            
            // Alle Java-Dateien hinzufügen
            List<VirtualFile> javaFiles = collectJavaFiles(projectInfo, indicator);
            LOG.info("Found " + javaFiles.size() + " Java files to analyze");
            
            if (javaFiles.isEmpty()) {
                LOG.warn("No Java files found in project");
                return groups;
            }
            
            indicator.setText("Analyzing " + javaFiles.size() + " files...");
            
            // Dateien zu CPD hinzufügen
            List<String> filePaths = new ArrayList<>();
            for (int i = 0; i < javaFiles.size(); i++) {
                if (indicator.isCanceled()) {
                    LOG.info("Analysis cancelled by user");
                    break;
                }
                
                VirtualFile file = javaFiles.get(i);
                indicator.setFraction((double) i / javaFiles.size());
                indicator.setText2("Processing: " + file.getName());
                
                addFileToCPD(config, file);
                filePaths.add(file.getPath());
            }
            
            // CPD-Analyse durchführen
            indicator.setText("Running CPD analysis...");
            CpdAnalysis cpd = CpdAnalysis.create(config);
            cpd.performAnalysis(report -> {
                // Ergebnisse verarbeiten
                indicator.setText("Processing results...");
                groups.addAll(processMatches(report.getMatches().iterator(), projectInfo.getProject()));
            });
            
            LOG.info("Duplicate code detection completed. Found " + groups.size() + " duplicate groups");
            
        } catch (Exception e) {
            LOG.error("Error during duplicate code detection", e);
        }
        
        return groups;
    }
    
    @NotNull
    private CPDConfiguration createCPDConfiguration() {
        CPDConfiguration config = new CPDConfiguration();
        config.setMinimumTileSize(minimumTileSize);
        config.setIgnoreAnnotations(ignoreAnnotations);
        config.setIgnoreIdentifiers(ignoreIdentifiers);
        config.setIgnoreLiterals(ignoreLiterals);
        
        LOG.debug("CPD Configuration: " + 
                 "minimumTileSize=" + minimumTileSize +
                 ", ignoreIdentifiers=" + ignoreIdentifiers +
                 ", ignoreLiterals=" + ignoreLiterals);
        
        return config;
    }
    
    @NotNull
    private List<VirtualFile> collectJavaFiles(@NotNull ProjectInfo projectInfo,
                                                @NotNull ProgressIndicator indicator) {
        return ReadAction.compute(() -> {
            List<VirtualFile> javaFiles = new ArrayList<>();
            
            // Alle Klassen durchgehen und deren Dateien sammeln
            projectInfo.getClasses().forEach(psiClass -> {
                if (indicator.isCanceled()) {
                    return;
                }
                
                PsiFile containingFile = psiClass.getContainingFile();
                if (containingFile != null) {
                    VirtualFile virtualFile = containingFile.getVirtualFile();
                    if (virtualFile != null && !javaFiles.contains(virtualFile)) {
                        javaFiles.add(virtualFile);
                    }
                }
            });
            
            return javaFiles;
        });
    }
    
    /**
     * Fügt eine Datei zu CPD hinzu.
     * 
     * @param config CPD-Configuration
     * @param file VirtualFile
     */
    private void addFileToCPD(@NotNull CPDConfiguration config, @NotNull VirtualFile file) {
        try {
            config.addInputPath(Paths.get(file.getPath()));
        } catch (Exception e) {
            LOG.warn("Error adding file to CPD: " + file.getPath(), e);
        }
    }
    
    /**
     * Verarbeitet CPD-Matches und konvertiert zu DuplicateCodeGroups.
     * 
     * @param matches Iterator über CPD-Matches
     * @param project IntelliJ Project
     * @return Set von DuplicateCodeGroups
     */
    @NotNull
    private Set<DuplicateCodeGroup> processMatches(@NotNull Iterator<Match> matches,
                                                    @NotNull Project project) {
        Set<DuplicateCodeGroup> groups = new HashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        
        int matchCount = 0;
        int validGroupCount = 0;
        
        while (matches.hasNext()) {
            Match match = matches.next();
            matchCount++;
            
            // DEBUGGING: Log Match Details
            LOG.info("=== Match #" + matchCount + " ===");
            LOG.info("  Token Count: " + match.getTokenCount());
            LOG.info("  Mark Count: " + match.getMarkCount());
            LOG.info("  Line Count: " + match.getLineCount());
            
            int markIndex = 0;
            for (Mark mark : match) {
                markIndex++;
                LOG.info("    Mark #" + markIndex + ": " + mark.getLocation().getFileId().getAbsolutePath() + 
                        " [" + mark.getLocation().getStartLine() + "-" + mark.getLocation().getEndLine() + "]");
            }
            
            try {
                DuplicateCodeGroup group = createGroupFromMatch(match, psiManager);
                LOG.info("  Created group with " + group.getOccurrences() + " occurrences");
                
                if (group.getOccurrences() >= 2) {
                    groups.add(group);
                    validGroupCount++;
                    LOG.info("  Group added (valid)");
                } else {
                    LOG.info(group.toString());
                    LOG.info(group.getFirstFragment().toString());
                    LOG.info("  Group rejected (< 2 occurrences)");
                }
            } catch (Exception e) {
                LOG.warn("Error processing match", e);
            }
        }
        
        LOG.info("=== SUMMARY ===");
        LOG.info("Total matches found by PMD: " + matchCount);
        LOG.info("Valid groups created: " + validGroupCount);
        
        return groups;
    }
    
    /**
     * Erstellt eine DuplicateCodeGroup aus einem CPD-Match.
     */
    @NotNull
    private DuplicateCodeGroup createGroupFromMatch(@NotNull Match match,
                                                     @NotNull PsiManager psiManager) {
        int tokenCount = match.getTokenCount();
        DuplicateCodeGroup group = new DuplicateCodeGroup(tokenCount);
        
        // Alle Markierungen (Occurrences) durchgehen
        for (Mark mark : match.getMarkSet()) {
            DuplicateCodeFragment fragment = createFragmentFromMark(mark, psiManager, tokenCount);
            if (fragment != null) {
                group.addFragment(fragment);
            }
        }
        
        return group;
    }
    
    /**
     * Erstellt ein DuplicateCodeFragment aus einer CPD-Mark.
     */
    private DuplicateCodeFragment createFragmentFromMark(@NotNull Mark mark,
                                                          @NotNull PsiManager psiManager,
                                                          int tokenCount) {
        return ReadAction.compute(() -> {
            try {
                String filePath = mark.getLocation().getFileId().getAbsolutePath();
                int startLine = mark.getLocation().getStartLine();
                int endLine = mark.getLocation().getEndLine();
                
                // Finde PsiFile
                VirtualFile virtualFile = findVirtualFile(filePath);
                if (virtualFile == null) {
                    LOG.warn("Could not find VirtualFile for: " + filePath);
                    return null;
                }
                
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile == null) {
                    LOG.warn("Could not find PsiFile for: " + filePath);
                    return null;
                }
                
                Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
                if (document == null) {
                    LOG.warn("Document not found for file: " + filePath);
                    return null;
                }
                
                int startOffset = document.getLineStartOffset(startLine - 1);
                int endOffset = document.getLineEndOffset(endLine - 1);
                
                PsiElement startElement = psiFile.findElementAt(startOffset);
                PsiElement endElement = psiFile.findElementAt(endOffset);
                
                if (startElement == null || endElement == null) {
                    LOG.warn("Could not find PSI elements for range: " + filePath + " [" + startLine + "-" + endLine + "]");
                    return null;
                }
                
                //Ist der Bereich bereits eine komplette Methode?
                PsiMethod startMethod = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class);
                PsiMethod endMethod = PsiTreeUtil.getParentOfType(endElement, PsiMethod.class);
                
                boolean isCompleteMethod = startMethod != null && startMethod == endMethod &&
                    startMethod.getTextRange().getStartOffset() == startOffset &&
                    startMethod.getTextRange().getEndOffset() == endOffset;
                
                //Ist der Bereich innerhalb EINER Methode?
                boolean isWithinSingleMethod = startMethod != null && startMethod == endMethod;
                
                //Hat der Bereich balanced Braces?
                String pmdCode = extractCode(psiFile, startLine, endLine);
                boolean hasBalancedBraces = isBalanced(pmdCode);
                
                //PMD direkt verwenden oder adjustieren?
                boolean usePmdDirectly = (isCompleteMethod || (isWithinSingleMethod && hasBalancedBraces));
                
                if (usePmdDirectly) {
                    //PMD-Bereich ist bereits refaktorierbar → direkt verwenden
                    LOG.debug("Using PMD range directly: " + filePath + " [" + startLine + "-" + endLine + "]");
                    
                    DuplicateCodeFragment fragment = new DuplicateCodeFragment(
                        psiFile, startLine, endLine, tokenCount, pmdCode
                    );
                    fragment.setPsiElement(startElement);
                    return fragment;
                    
                } else {
                    //PMD-Bereich ist problematisch → Adjuster verwenden
                    LOG.debug("Adjusting PMD range: " + filePath + " [" + startLine + "-" + endLine + "]" +
                        " (isCompleteMethod=" + isCompleteMethod + ", isWithinSingleMethod=" + isWithinSingleMethod + 
                        ", hasBalancedBraces=" + hasBalancedBraces + ")");
                    
                    PsiElement adjustedElement = DuplicateRangeAdjuster.adjustRange(
                        psiFile, document, startLine, endLine
                    );
                    
                    if (adjustedElement == null) {
                        LOG.warn("Could not adjust range for: " + filePath + " [" + startLine + "-" + endLine + "]");
                        return null;
                    }
                    
                    int adjustedStartLine = document.getLineNumber(adjustedElement.getTextRange().getStartOffset()) + 1;
                    int adjustedEndLine = document.getLineNumber(adjustedElement.getTextRange().getEndOffset()) + 1;
                    
                    String code = adjustedElement.getText();
                    DuplicateCodeFragment fragment = new DuplicateCodeFragment(
                        psiFile, adjustedStartLine, adjustedEndLine, tokenCount, code
                    );
                    fragment.setPsiElement(adjustedElement);
                    
                    return fragment;
                }
                
            } catch (Exception e) {
                LOG.warn("Error creating fragment from mark", e);
                return null;
            }
        });
    }
    
    /**
     * Prüft ob ein Code-String balanced Braces hat (gleiche Anzahl { und }).
     */
    private boolean isBalanced(@NotNull String code) {
        int openCount = 0;
        int closeCount = 0;
        
        for (char c : code.toCharArray()) {
            if (c == '{') openCount++;
            else if (c == '}') closeCount++;
        }
        
        return openCount == closeCount;
    }
    
    /**
     * Findet VirtualFile anhand Pfad.
     */
    private VirtualFile findVirtualFile(@NotNull String path) {
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path);
    }
    
    /**
     * Extrahiert Code-Text aus PsiFile zwischen Zeilen.
     */
    @NotNull
    private String extractCode(@NotNull PsiFile psiFile, int startLine, int endLine) {
        String text = psiFile.getText();
        String[] lines = text.split("\n");
        
        if (startLine < 1 || endLine > lines.length) {
            LOG.warn("Invalid line range: " + startLine + "-" + endLine + " for file with " + lines.length + " lines");
            return "";
        }
        
        StringBuilder code = new StringBuilder();
        for (int i = startLine - 1; i < endLine && i < lines.length; i++) {
            code.append(lines[i]).append("\n");
        }
        
        return code.toString().trim();
    }
    
    /**
     * Gibt die aktuelle Konfiguration zurück.
     */
    @NotNull
    public String getConfiguration() {
        return "PMDDuplicateCodeDetector{" +
               "minimumTileSize=" + minimumTileSize +
               ", ignoreIdentifiers=" + ignoreIdentifiers +
               ", ignoreLiterals=" + ignoreLiterals +
               ", ignoreAnnotations=" + ignoreAnnotations +
               '}';
    }
}
