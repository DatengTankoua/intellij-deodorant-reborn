# Projektdokumentation

**Projekt:** IntelliJDeodorant  Erweiterung um Duplicate-Code-Erkennung und kontextadaptive Refactoring-Strategien  
**Version:** 2025.2-COMPATIBLE-1.0  
**Autor:** Dateng Tankoua Emery Josian  
**Institution:** Philipps-Universität Marburg  
**Kontext:** Bachelor-Arbeit  
**Basis-Repository:** [JetBrains-Research/IntelliJDeodorant](https://github.com/JetBrains-Research/IntelliJDeodorant)

---

## Zielsetzung

**IntelliJDeodorant** ist ein IntelliJ-IDEA-Plugin zur statischen Erkennung von Code-Smells in Java-Projekten und zur Bereitstellung halbautomatischer Refactoring-Aktionen. Das Plugin detektiert strukturelle Anti-Patterns, quantifiziert deren Schwere und ermöglicht direkt ausführbare Korrekturen.

Die vorliegende Thesis-Version erweitert das bestehende System um eine vollständige **Duplicate-Code-Analyse-Pipeline** auf Basis von PMD CPD sowie vier differenzierter, klassenstrukturauflösender Refactoring-Strategien, die automatisch anhand der Vererbungshierarchie der betroffenen Klassen ausgewählt werden.

---

## High-Level-Architektur

\
                  IntelliJ Platform (PSI/EDT)             

   Tool Window UI         Actions (Tools-Menü)           
   (5 Tabs / Panels)      DetectDuplicateCodeAction [N]  

              Refactoring Strategy Layer [N]              
  ExtractMethod | ExtractAndPullUp |                      
  ExtractSuperclass | ExtractUtilityMethod                

           Core Detection & Validation Layer              
  PMDDuplicateCodeDetector [N] | DuplicateCodeValidator   
  ExtractMethodFeasibilityChecker [N]                     
  DuplicateSimilarityChecker [N]                          

   AST/PDG Engine      Distance/Clustering Engine        
  (core.ast + cfg)     (core.distance)                   

              IntelliJ PSI / VFS / SDK                    

[N] = Neue Implementierung (Thesis)
\
---

## Unterstützte Code-Smells und Refactoring-Strategien

| Code Smell | Refactoring | Status |
|---|---|---|
| Feature Envy | Move Method | Bestehendes System |
| Long Method | Extract Method (PDG-basiert) | Bestehendes System |
| God Class | Extract Class | Bestehendes System |
| Type Checking | Replace Conditional with Polymorphism | Bestehendes System |
| State Checking | Replace Type Code with State/Strategy | Bestehendes System |
| **Duplicate Code** | **Extract Method / Pull-Up / Superclass / Utility** | **Neu (Thesis)** |

---

## Systemabgrenzung: Bestehendes System vs. Neue Implementierungen

### Bestehendes System (JetBrains-Research/IntelliJDeodorant upstream)

- Vollständige AST-Modellschicht (core.ast.*) inkl. CFG/PDG-Engine (core.ast.decomposition.cfg, ~50 Klassen)
- Distanz- und Clustering-Analyse (core.distance.*) für Feature-Envy und God-Class
- Alle vier Original-Refactoring-Implementierungen (ide.refactoring.moveMethod/extractMethod/extractClass/typeStateChecking)
- Tool-Window-Infrastruktur mit vier Tabs, AbstractRefactoringPanel, JDeodorantFacade
- Reporting-Infrastruktur (GitHubErrorReporter), FUS-Logging, Vererbungsanalyse, NLP-Utilities

### Neue Implementierungen (Thesis)

| Schicht | Klasse | Funktion |
|---|---|---|
| Detection | PMDDuplicateCodeDetector | Token-basierte Klonerkennung via PMD CPD v7.7.0 |
| Detection | DuplicateCodeFragment / DuplicateCodeGroup | Datenmodell für Kloninstanzen und Klonmengen |
| Detection | DuplicateRangeAdjuster | Abbildung von PMD-Zeilenbereichen auf PSI-Statement-Grenzen |
| Validation | DuplicateCodeValidator | Orchestrierungs-Pipeline: Feasibility -> Similarity |
| Validation | ExtractMethodFeasibilityChecker | Prüft Extract-Method-Ausführbarkeit via ExtractMethodProcessor.prepare() |
| Validation | DuplicateSimilarityChecker | Strukturelle Ähnlichkeitsprüfung via IntelliJ DuplicatesFinder |
| Orchestration | DuplicateCodeRefactoringHandler | LCA-Analyse, Strategieauswahl, Bestätigungsdialog |
| Orchestration | DetectDuplicateCodeAction | AnAction-Einstiegspunkt im Tools-Menü |
| Strategy | DuplicateRefactoringStrategy (abstract) | Basisklasse mit Hilfsmethoden: 
unExtractMethod(), askUserForClassName() etc. |
| Strategy | ExtractMethodStrategy | Strategie 1: Intra-Klassen-Duplikat |
| Strategy | ExtractAndPullUpStrategy | Strategie 2: Gemeinsame Superklasse im Projekt |
| Strategy | ExtractSuperclassStrategy | Strategie 3: Externe Superklasse -> neue Zwischenklasse |
| Strategy | ExtractUtilityMethodStrategy | Strategie 4: Keine Hierarchiebeziehung -> statische Utility-Methode |
| UI | DuplicateCodePanel | JBTable mit Editor-Highlighting, Tooltip-Locations, Background-Task |
| UI | DuplicateCodeTableModel | Swing-Tabellenmodell, nach Severity absteigend sortiert |
| Test | DuplicateCodeTest | Unit-Tests für alle neuen Komponenten |

---

## Build-Konfiguration

| Parameter | Wert |
|---|---|
| Plugin-ID | org.jetbrains.research.intellijdeodorant |
| Version | 2025.2-COMPATIBLE-1.0 |
| Ziel-IDE | IntelliJ IDEA IC, Build 213252 (2021.32025.2) |
| Java | 11 (Source + Target) |
| Build-System | Gradle + IntelliJ Platform Gradle Plugin |
| Neue externe Abhängigkeit | pmd-core:7.7.0, pmd-java:7.7.0 |
