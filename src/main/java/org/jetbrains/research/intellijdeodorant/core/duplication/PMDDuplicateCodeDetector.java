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
        long currentModificationCount = project.getService(PsiModificationTracker.class).getModificationCount();
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
        int validGroupCount = 0;
        int mergedGroupCount = 0;
        
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
                DuplicateCodeGroup newGroup = createGroupFromMatch(match, psiManager);
                LOG.info("  Created group with " + newGroup.getOccurrences() + " occurrences");
                
                if (newGroup.getOccurrences() >= 2) {
                    // Prüfe ob eines der Fragmente bereits in einer existierenden Gruppe ist
                    DuplicateCodeGroup existingGroup = findGroupContainingAnyFragment(groups, newGroup);
                    
                    if (existingGroup != null) {
                        // Merge: Füge alle Fragmente der neuen Gruppe zur existierenden hinzu
                        for (DuplicateCodeFragment fragment : newGroup.getFragments()) {
                            existingGroup.addFragment(fragment);
                        }
                        mergedGroupCount++;
                        LOG.info("  Group merged into existing group (now " + existingGroup.getOccurrences() + " occurrences)");
                    } else {
                        // Neue Gruppe hinzufügen
                        groups.add(newGroup);
                        validGroupCount++;
                        LOG.info("  Group added (valid)");
                    }
                } else {
                    LOG.info(newGroup.toString());
                    LOG.info(newGroup.getFirstFragment().toString());
                    LOG.info("  Group rejected (< 2 occurrences)");
                }
            } catch (Exception e) {
                LOG.warn("Error processing match", e);
            }
        }
        
        LOG.info("=== SUMMARY ===");
        LOG.info("Total matches found by PMD: " + matchCount);
        LOG.info("Valid groups created: " + validGroupCount);
        LOG.info("Groups merged: " + mergedGroupCount);
        
        // Validiere Variablentypen und Code-Struktur
        int removedByTypeCheck = validateGroups(groups, this::extractVariableTypesFromLines, "type");
        int removedByStructureCheck = validateGroups(groups, (f, p) -> extractCodeSignature(f), "structure");
        
        LOG.info("Fragments removed due to type incompatibility: " + removedByTypeCheck);
        LOG.info("Fragments removed due to structure incompatibility: " + removedByStructureCheck);
        
        // Entferne Gruppen mit zu wenigen Fragmenten, nicht refactorable oder zu kurzen Fragmenten
        groups.removeIf(group -> group.getOccurrences() < 2 || !isRefactorable(group.getFirstFragment()) || group.getFirstFragment().getLineCount() < 7);
        
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
     * Functional Interface für Fragment-Validierung.
     */
    @FunctionalInterface
    private interface FragmentExtractor {
        Object extract(DuplicateCodeFragment fragment, PsiFile psiFile);
    }

    /**
     * Generische Validierungsmethode mit Mehrheitsprinzip.
     * Extrahiert Eigenschaften aus Fragmenten, findet Mehrheit, entfernt Ausreißer.
     */
    private int validateGroups(@NotNull Set<DuplicateCodeGroup> groups,
                               @NotNull FragmentExtractor extractor,
                               @NotNull String validationType) {
        int removedCount = 0;
        
        for (DuplicateCodeGroup group : groups) {
            if (group.getOccurrences() < 2) continue;
            
            // 1. Sammle Eigenschaften aus ALLEN Fragmenten
            Map<Object, Integer> propertyCounts = new HashMap<>();
            Map<DuplicateCodeFragment, Object> fragmentProperties = new HashMap<>();
            
            for (DuplicateCodeFragment fragment : group.getFragments()) {
                Object property = ReadAction.compute(() -> extractor.extract(fragment, fragment.getFile()));
                fragmentProperties.put(fragment, property);
                propertyCounts.merge(property, 1, Integer::sum);
            }
            
            // 2. Finde Mehrheits-Eigenschaft
            Object majorityProperty = propertyCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (majorityProperty == null) continue;
            
            // 3. Entferne Fragmente mit abweichender Eigenschaft
            for (Map.Entry<DuplicateCodeFragment, Object> entry : fragmentProperties.entrySet()) {
                if (!entry.getValue().equals(majorityProperty)) {
                    group.removeFragment(entry.getKey());
                    removedCount++;
                    LOG.info("Removing fragment with incompatible " + validationType + ": " + 
                            entry.getKey().getLocationString());
                }
            }
        }
        
        return removedCount;
    }

    /**
     * Extrahiert Code-Signatur aus gespeicherten Statements.
     */
    @NotNull
    private String extractCodeSignature(@NotNull DuplicateCodeFragment fragment) {
        PsiElement[] statements = fragment.getStatements();
        if (statements == null || statements.length == 0) return "";
        
        StringBuilder signature = new StringBuilder();
        for (PsiElement stmt : statements) {
            // Sammle alle Child-Elemente
            PsiTreeUtil.processElements(stmt, element -> {
                if (element instanceof PsiMethodCallExpression) {
                    PsiMethod method = ((PsiMethodCallExpression) element).resolveMethod();
                    if (method != null) {
                        int paramCount = ((PsiMethodCallExpression) element).getArgumentList().getExpressionCount();
                        signature.append("M:").append(method.getName()).append(":").append(paramCount).append(";");
                    }
                } else if (element instanceof PsiBinaryExpression) {
                    signature.append("OP:").append(((PsiBinaryExpression) element).getOperationTokenType()).append(";");
                } else if (element instanceof PsiAssignmentExpression) {
                    signature.append("A;");
                }
                return true;
            });
        }
        return signature.toString();
    }
    
    /**
     * Extrahiert Variablentypen aus gespeicherten Statements (ohne Namen).
     */
    @NotNull
    private String extractVariableTypesFromLines(@NotNull DuplicateCodeFragment fragment, @NotNull PsiFile psiFile) {
        PsiElement[] statements = fragment.getStatements();
        if (statements == null || statements.length == 0) return "";
        
        List<String> types = new ArrayList<>();
        for (PsiElement stmt : statements) {
            // Sammle Variablen aus allen Child-Elementen
            PsiTreeUtil.processElements(stmt, element -> {
                if (element instanceof PsiReferenceExpression) {
                    PsiElement resolved = ((PsiReferenceExpression) element).resolve();
                    if (resolved instanceof PsiVariable) {
                        addVariableType(types, (PsiVariable) resolved);
                    }
                } else if (element instanceof PsiVariable) {
                    addVariableType(types, (PsiVariable) element);
                }
                return true;
            });
        }
        
        return String.join(";", types);
    }

    private void addVariableType(List<String> typeList, PsiVariable var) {
        PsiType type = var.getType();
        if (type != null) {
            typeList.add(type.getCanonicalText());
        }
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
        for (Mark mark : match) {
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
                
                // Verwende DuplicateRangeAdjuster für konsistente Statement-Extraktion
                DuplicateRangeAdjuster.AdjustedRange adjustedRange = DuplicateRangeAdjuster.adjustRangeWithLines(
                    psiFile, document, startLine, endLine
                );
                
                if (adjustedRange == null) {
                    LOG.warn("Could not adjust range for: " + filePath + " [" + startLine + "-" + endLine + "]");
                    return null;
                }
                
                // Erstelle Fragment mit angepassten Werten
                DuplicateCodeFragment fragment = new DuplicateCodeFragment(
                    psiFile, 
                    adjustedRange.getStartLine(), 
                    adjustedRange.getEndLine(), 
                    tokenCount, 
                    adjustedRange.getCode()
                );
                fragment.setStatements(adjustedRange.getStatements());
                
                LOG.debug("Created fragment: " + filePath + " [" + adjustedRange.getStartLine() + "-" + adjustedRange.getEndLine() + 
                         "] with " + adjustedRange.getStatements().length + " statements");
                
                return fragment;
                
            } catch (Exception e) {
                LOG.warn("Error creating fragment from mark", e);
                return null;
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

    /**
     * Prüft ob Fragment refactoring-fähig ist (via ExtractMethodProcessor).
     */
    private boolean isRefactorable(@NotNull DuplicateCodeFragment fragment) {
        return ReadAction.compute(() -> {
            try {
                PsiFile psiFile = fragment.getFile();
                if (psiFile == null) return false;
                
                // Nutze die bereits extrahierten Statements
                PsiElement[] statements = fragment.getStatements();
                if (statements == null || statements.length == 0) return false;
                
                ExtractMethodProcessor processor = new ExtractMethodProcessor(
                    psiFile.getProject(), null, 
                    statements,
                    null, "RefactoringTest", null, null
                );
                
                return processor.prepare();
            } catch (Exception e) {
                return false;
            }
        });
    }
}
