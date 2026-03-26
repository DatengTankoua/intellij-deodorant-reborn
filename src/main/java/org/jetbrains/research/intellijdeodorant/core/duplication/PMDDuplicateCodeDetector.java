package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;

import net.sourceforge.pmd.cpd.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.core.distance.ProjectInfo;
import org.jetbrains.research.intellijdeodorant.utils.DuplicateRangeAdjuster;

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
    private boolean ignoreIdentifiers = true;
    private boolean ignoreLiterals = true;

    // Cache für Detection-Ergebnisse
    private Set<DuplicateCodeGroup> cachedResults = null;
    private long lastModificationCount = -1;
    private int lastAnalyzedFileCount = -1;
    private int lastMinimumTileSize = -1;
    
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
        
        if (this.minimumTileSize != minimumTileSize) {
            this.minimumTileSize = minimumTileSize;
            invalidateCache();
        }
    }
    
    public void setIgnoreIdentifiers(boolean ignoreIdentifiers) {
        if (this.ignoreIdentifiers != ignoreIdentifiers) {
            this.ignoreIdentifiers = ignoreIdentifiers;
            invalidateCache();
        }
    }
    
    public void setIgnoreLiterals(boolean ignoreLiterals) {
        if (this.ignoreLiterals != ignoreLiterals) {
            this.ignoreLiterals = ignoreLiterals;
            invalidateCache();
        }
    }
    
    @NotNull
    public Set<DuplicateCodeGroup> detectDuplicates(@NotNull ProjectInfo projectInfo,
                                                     @NotNull ProgressIndicator indicator) {
        LOG.info("Starting duplicate code detection with minimumTileSize=" + minimumTileSize);

        // Prüfe ob Cache verwendet werden kann
        Project project = projectInfo.getProject();
        long currentModificationCount = PsiManager.getInstance(project).getModificationTracker().getModificationCount();
        List<VirtualFile> javaFiles = collectJavaFiles(projectInfo, indicator);
        int currentFileCount = javaFiles.size();
        
        if (cachedResults != null && 
            lastModificationCount == currentModificationCount &&
            lastAnalyzedFileCount == currentFileCount &&
            lastMinimumTileSize == minimumTileSize) {
            LOG.info("Using cached duplicate code detection results (" + cachedResults.size() + " groups)");
            indicator.setText("Using cached results...");
            return new HashSet<>(cachedResults); // Return defensive copy
        }
        
        LOG.info("Cache miss - performing full analysis");
        LOG.info("  Modification count: " + lastModificationCount + " -> " + currentModificationCount);
        LOG.info("  File count: " + lastAnalyzedFileCount + " -> " + currentFileCount);
        LOG.info("  Tile size: " + lastMinimumTileSize + " -> " + minimumTileSize);
        
        indicator.setText("Initializing PMD CPD...");
        
        Set<DuplicateCodeGroup> groups = new HashSet<>();
        
        try {
            // CPD konfigurieren
            CPDConfiguration config = createCPDConfiguration();
            
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

            // Cache die Ergebnisse für zukünftige Aufrufe
            cachedResults = new HashSet<>(groups);
            lastModificationCount = currentModificationCount;
            lastAnalyzedFileCount = currentFileCount;
            lastMinimumTileSize = minimumTileSize;
            LOG.info("Results cached for future use");
            
        } catch (Exception e) {
            LOG.error("Error during duplicate code detection", e);
        }
        
        return groups;
    }

    /**
     * Diese Methode kann aufgerufen werden wenn man sicher ist dass sich etwas geändert hat, 
     * das die bisherigen Ergebnisse ungültig macht (z.B. Konfigurationsänderung, manuelles Invalidate).
     */
    public void invalidateCache() {
        cachedResults = null;
        lastModificationCount = -1;
        lastAnalyzedFileCount = -1;
        lastMinimumTileSize = -1;
        LOG.info("Cache invalidated - next detection will perform full analysis");
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
    
    /**
     * Sammelt alle Java-Dateien aus dem Projekt.
     * Ignoriert Test-Dateien und andere ausgeschlossene Dateien.
     */
    @NotNull
    private List<VirtualFile> collectJavaFiles(@NotNull ProjectInfo projectInfo,
                                                @NotNull ProgressIndicator indicator) {
        return ReadAction.compute(() -> {
            List<VirtualFile> javaFiles = new ArrayList<>();
            int ignoredCount = 0;
            
            // Alle Klassen durchgehen und deren Dateien sammeln
            for (com.intellij.psi.PsiClass psiClass : projectInfo.getClasses()) {
                if (indicator.isCanceled()) {
                    break;
                }
                
                PsiFile containingFile = psiClass.getContainingFile();
                if (containingFile != null) {
                    VirtualFile virtualFile = containingFile.getVirtualFile();
                    if (virtualFile != null && !javaFiles.contains(virtualFile)) {
                        // Prüfe ob Datei ignoriert werden soll
                        if (shouldIgnoreFile(virtualFile)) {
                            ignoredCount++;
                            LOG.debug("Ignoring file: " + virtualFile.getPath());
                        } else {
                            javaFiles.add(virtualFile);
                        }
                    }
                }
            }
            
            LOG.info("Collected " + javaFiles.size() + " Java files (ignored " + ignoredCount + " test/generated files)");
            return javaFiles;
        });
    }

    /**
     * Prüft ob eine Datei ignoriert werden soll.
     */
    private boolean shouldIgnoreFile(@NotNull VirtualFile file) {
        String path = file.getPath();        
        // Test-Verzeichnisse
        if (path.contains("/test/") || path.contains("\\test\\")) {
            return true;
        }
        
        // Generierte Dateien
        if (path.contains("/target/") || path.contains("\\target\\") ||
            path.contains("/build/") || path.contains("\\build\\") ||
            path.contains("/generated/") || path.contains("\\generated\\")) {
            return true;
        }
        
        return false;
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
        int adjustedGroupCount = 0;
        
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
                List<DuplicateCodeGroup> newGroups = createGroupsFromMatch(match, psiManager);
                adjustedGroupCount += newGroups.size();
                LOG.info("  Created " + newGroups.size() + " group(s) from match");

                for (DuplicateCodeGroup newGroup : newGroups) {
                    LOG.info("  Group with " + newGroup.getOccurrences() + " occurrences");
                    // Prüfe ob eines der Fragmente bereits in einer existierenden Gruppe ist
                    DuplicateCodeGroup existingGroup = findGroupContainingAnyFragment(groups, newGroup);

                    if (existingGroup != null) {
                        // Merge: Füge alle Fragmente der neuen Gruppe zur existierenden hinzu
                        for (DuplicateCodeFragment fragment : newGroup.getFragments()) {
                            existingGroup.addFragment(fragment);
                        }
                        LOG.info("  Group merged into existing group (now " + existingGroup.getOccurrences() + " occurrences)");
                    } else {
                        groups.add(newGroup);
                        LOG.info("  Group added (valid)");
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error processing match", e);
            }
        }
        
        // Validierung: Typen, Struktur, Output-Variablen, Kontrollfluss, Extrahierbarkeit
        DuplicateCodeValidator.validate(groups, matchCount, adjustedGroupCount);

        return groups;
    }

    /**
     * Findet eine existierende Gruppe, die eines der Fragmente der neuen Gruppe enthält.
     */
    @Nullable
    private DuplicateCodeGroup findGroupContainingAnyFragment(@NotNull Set<DuplicateCodeGroup> existingGroups,
                                                               @NotNull DuplicateCodeGroup newGroup) {
        for (DuplicateCodeFragment newFragment : newGroup.getFragments()) {
            for (DuplicateCodeGroup existingGroup : existingGroups) {
                for (DuplicateCodeFragment existingFragment : existingGroup.getFragments()) {
                    if (newFragment.equals(existingFragment)) {
                        return existingGroup;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Erstellt pro Methoden-Schnitt eine eigene DuplicateCodeGroup aus einem CPD-Match.
     * @param match      CPD-Match
     * @param psiManager PSI-Manager
     * @return Liste von Gruppen (eine pro Methoden-Position)
     */
    @NotNull
    private List<DuplicateCodeGroup> createGroupsFromMatch(@NotNull Match match,
                                                            @NotNull PsiManager psiManager) {
        int tokenCount = match.getTokenCount();

        // Pro Mark die zugehörigen Sub-Fragmente ermitteln
        List<List<DuplicateCodeFragment>> fragmentsPerMark = new ArrayList<>();
        for (Mark mark : match) {
            fragmentsPerMark.add(createFragmentsFromMark(mark, psiManager, tokenCount));
        }

        // i-te Sub-Fragmente aller Marks → eigene Gruppe
        int maxSplits = fragmentsPerMark.stream().mapToInt(List::size).max().orElse(0);
        List<DuplicateCodeGroup> groups = new ArrayList<>();
        for (int i = 0; i < maxSplits; i++) {
            DuplicateCodeGroup group = new DuplicateCodeGroup(tokenCount);
            for (List<DuplicateCodeFragment> frags : fragmentsPerMark) {
                if (i < frags.size()) {
                    group.addFragment(frags.get(i));
                }
            }
            if (group.getOccurrences() >= 2) {
                groups.add(group);
            }
        }
        return groups;
    }
    
    /**
     * Erstellt DuplicateCodeFragments aus einer CPD-Mark.
     * Berührt das Mark mehrere Fragmente, wird pro Fragment ein eigenes DuplicateCodeFragment erstellt.
     *
     * @param mark       CPD-Mark
     * @param psiManager PSI-Manager
     * @param tokenCount Anzahl Tokens für dieses Match
     * @return Liste von Fragmenten
     */
    @NotNull
    private List<DuplicateCodeFragment> createFragmentsFromMark(@NotNull Mark mark,
                                                                  @NotNull PsiManager psiManager,
                                                                  int tokenCount) {
        return ReadAction.compute(() -> {
            List<DuplicateCodeFragment> fragments = new ArrayList<>();
            try {
                String filePath = mark.getLocation().getFileId().getAbsolutePath();
                int startLine = mark.getLocation().getStartLine();
                int endLine = mark.getLocation().getEndLine();
                
                // Finde PsiFile
                VirtualFile virtualFile = findVirtualFile(filePath);
                if (virtualFile == null) {
                    LOG.warn("Could not find VirtualFile for: " + filePath);
                    return fragments;
                }
                
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile == null) {
                    LOG.warn("Could not find PsiFile for: " + filePath);
                    return fragments;
                }
                
                Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
                if (document == null) {
                    LOG.warn("Document not found for file: " + filePath);
                    return fragments;
                }
                
                // Pro überschnittener Methode einen eigenen Refactoring-Kandidaten erstellen
                for (DuplicateRangeAdjuster.AdjustedRange adjustedRange :
                        DuplicateRangeAdjuster.adjustRangeWithLines(psiFile, document, startLine, endLine)) {
                    int adjustedTokens = DuplicateRangeAdjuster.countTokens(adjustedRange.statements);
                    DuplicateCodeFragment fragment = new DuplicateCodeFragment(
                        psiFile,
                        adjustedRange.startLine,
                        adjustedRange.endLine,
                        adjustedTokens,
                        adjustedRange.code
                    );
                    fragment.setStatements(adjustedRange.statements);
                    LOG.debug("Created fragment: " + filePath + " [" + adjustedRange.startLine + "-" + adjustedRange.endLine +
                             "] with " + adjustedRange.statements.length + " statements");
                    fragments.add(fragment);
                }
                if (fragments.isEmpty()) {
                    LOG.warn("No fragments adjusted for: " + filePath + " [" + startLine + "-" + endLine + "]");
                }
                return fragments;

            } catch (Exception e) {
                LOG.warn("Error creating fragments from mark", e);
                return fragments;
            }
        });
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
