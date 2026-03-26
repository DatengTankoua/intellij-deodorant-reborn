# Entwicklerdokumentation

**Projekt:** IntelliJDeodorant — Thesis-Erweiterung  
**Version:** 2025.2-COMPATIBLE-1.0  
**Zielgruppe:** Plugin-Entwickler, Contributors, Maintainer  
**Voraussetzungen:** Java 11+, Gradle, IntelliJ Platform SDK-Kenntnisse

---

## Build-Konfiguration

| Parameter | Wert |
|---|---|
| Plugin-ID | `org.jetbrains.research.intellijdeodorant` |
| Gruppe | `org.jetbrains.research` |
| Version | `2025.2-COMPATIBLE-1.0` |
| Ziel-IDE | IntelliJ IDEA IC 2021.3, Build 213–252 |
| Java | 11 (Source + Target) |
| Build-System | Gradle + IntelliJ Platform Gradle Plugin |
| Neue externe Abhängigkeit | `pmd-core:7.7.0`, `pmd-java:7.7.0` |

### Build-Befehle

```bash
# Plugin bauen
./gradlew buildPlugin

# Tests ausführen
./gradlew test

# Plugin in Sandbox-IDE starten
./gradlew runIde
```

---

## Paketstruktur

```
org.jetbrains.research.intellijdeodorant/
├── core/
│   ├── ast/                      [ORIGINAL] AST-Modellschicht
│   │   ├── decomposition/        [ORIGINAL] Statement-Dekomposition
│   │   │   └── cfg/              [ORIGINAL] CFG + PDG (~50 Klassen)
│   │   └── util/                 [ORIGINAL] PSI-Pattern-Checker (InstanceOf*)
│   ├── distance/                 [ORIGINAL] Distanz- & Clustering-Analyse
│   └── duplication/              [NEU] Klonerkennung & Validation
├── ide/
│   ├── fus/                      [ORIGINAL] Feature-Usage-Statistics
│   ├── refactoring/
│   │   ├── extractClass/         [ORIGINAL] God-Class-Refactoring
│   │   ├── extractMethod/        [ORIGINAL] Long-Method-Refactoring
│   │   ├── moveMethod/           [ORIGINAL] Feature-Envy-Refactoring
│   │   ├── typeStateChecking/    [ORIGINAL] Polymorphism-Refactoring
│   │   └── duplicateCode/        [NEU] Duplicate-Code-Refactoring
│   │       └── strategy/         [NEU] 4 Refactoring-Strategien
│   └── ui/                       [ORIGINAL + NEU] Swing/JBComponents-Panels
├── inheritance/                  [ORIGINAL] Vererbungsbaum-Analyse
├── reporting/                    [ORIGINAL] GitHub-Fehlerreporter
└── utils/                        [ORIGINAL + NEU] Hilfsfunktionen
```

---

## Wichtige Klassen: Bestehendes System

### `JDeodorantFacade`
Zentrale statische Fassade. Exponiert vier Erkennungsmethoden:
- `getMoveMethodRefactoringOpportunities(ProjectInfo, ProgressIndicator)`
- `getExtractClassRefactoringOpportunities(ProjectInfo, ProgressIndicator)`
- `getExtractMethodRefactoringOpportunities(ProjectInfo, ProgressIndicator)`
- `getTypeCheckEliminationRefactoringOpportunities(ProjectInfo, ProgressIndicator)`

### `ASTReader`
Traversiert den IntelliJ-PSI-Baum und konstruiert eine `SystemObject`-Repräsentation des gesamten Projekts. Bildet die Bridge zwischen IntelliJ PSI und dem internen AST-Modell.

### CFG/PDG-Engine (`core.ast.decomposition.cfg`)
Vollständiger Control-Flow-Graph und Program-Dependence-Graph für jede Java-Methode. PDG-Slicing (`PDGSliceUnion`, `ASTSliceGroup`) identifiziert extrahierbare Codesequenzen für die Long-Method-Erkennung. Rechnerisch aufwändigste Komponente des Systems.

### `MyExtractMethodProcessor`
Wrapper um IntelliJs `ExtractMethodProcessor`. Kapselt die PSI-Transformation für Long-Method-Kandidaten aus der PDG-Analyse.

### `AbstractRefactoringPanel`
Basis-Swing-Panel für alle Original-Tabs. Enthält gemeinsame Logik für Refresh-Button, Scope-Chooser, Progress-Indikator und Refactor-Aktion.

---

## Wichtige Klassen: Neue Implementierungen (Thesis)

### `PMDDuplicateCodeDetector` — Kern der Klonerkennung

Integriert die PMD-CPD-API (v7.7.0) direkt in den IntelliJ-Lebenszyklus.

```java
// Konfiguration (Standardwerte)
detector.setMinimumTileSize(60);     // Token-Schwellwert
detector.setIgnoreIdentifiers(true); // Bezeichner normalisieren
detector.setIgnoreLiterals(true);    // Literale normalisieren

// Aufruf
Set<DuplicateCodeGroup> groups = detector.detectDuplicates(project, files, progress);
```

**Cache-Strategie:** Ergebnisse werden gecacht und bei geänderten Dateien oder geändertem Token-Schwellwert via `PsiModificationTracker.getModificationCount()` automatisch invalidiert.

---

### `DuplicateCodeValidator` — Dreistufige Validierungs-Pipeline

```
1. DuplicateRangeAdjuster.adjust()
   PMD-Zeilennummern -> PsiStatement-Arrays via PsiTreeUtil + Document.getLineNumber()

2. ExtractMethodFeasibilityChecker.validate()
   - Fragmente < 7 Zeilen: verworfen
   - ExtractMethodProcessor.prepare() auf jedem Fragment:
     nicht-extrahierbare Fragmente: verworfen

3. DuplicateSimilarityChecker.validate()
   - DuplicatesFinder (IntelliJ-intern) auf Fragmentpaaren
   - Gruppen mit divergenter Struktur werden aufgeteilt
```

---

### Refactoring-Strategie-Hierarchie

```
DuplicateRefactoringStrategy (abstract)
  |-- ExtractMethodStrategy
  |-- ExtractAndPullUpStrategy
  |-- ExtractSuperclassStrategy
  +-- ExtractUtilityMethodStrategy
```

**`RefactoringContext`** (innere Klasse von `DuplicateRefactoringStrategy`):

| Feld | Typ | Bedeutung |
|---|---|---|
| `fragments` | `List<DuplicateCodeFragment>` | Alle Kloninstanzen der Gruppe |
| `affectedClasses` | `List<PsiClass>` | Betroffene Klassen |
| `fragmentToClass` | `Map<DuplicateCodeFragment, PsiClass>` | Fragment-zu-Klassen-Zuordnung |
| `isSameClass` | `boolean` | Alle Fragmente in derselben Klasse |
| `commonSuperClass` | `PsiClass` | LCA (niedrigste gemeinsame Superklasse) |
| `superClassIsInSource` | `boolean` | LCA liegt im editierbaren Projektquellcode |

---

### Strategieauswahl in `DuplicateCodeRefactoringHandler`

```java
if (ctx.isSameClass)
    return new ExtractMethodStrategy(project);

if (ctx.commonSuperClass != null) {
    if (ctx.superClassIsInSource)
        return new ExtractAndPullUpStrategy(project);   // Strategie 2
    else
        return new ExtractSuperclassStrategy(project);  // Strategie 3
}
return new ExtractUtilityMethodStrategy(project);       // Strategie 4
```

**LCA-Ermittlung:** Traversiert die `getSuperClass()`-Kette der ersten betroffenen Klasse; bricht bei `java.lang.Object` ab. Prüft jede Kandidaten-Klasse via `InheritanceUtil.isInheritorOrSelf()` gegen alle weiteren betroffenen Klassen.

**Quellcode-Prüfung:** `ProjectFileIndex.getInstance(project).isInSourceContent(virtualFile)` bestimmt, ob die Superklasse im Projekt editierbar ist.

---

### PSI-Manipulationen der neuen Strategien

#### Strategie 1: `ExtractMethodStrategy`
Ruft `ExtractMethodProcessor` auf dem ersten Fragment auf. IntelliJ ersetzt alle weiteren Duplikate innerhalb der Klasse automatisch.

#### Strategie 2: `ExtractAndPullUpStrategy`
1. `ExtractMethodProcessor` extrahiert Methode in Quellklasse (PSI-Schreibzugriff)
2. `PullUpProcessor` verschiebt `PsiMethod` in die gemeinsame Superklasse
3. `PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true)` setzt Sichtbarkeit

#### Strategie 3: `ExtractSuperclassStrategy`
1. `PsiElementFactory.createClass()` — neue Klasse mit `extends ExternalSuperClass`
2. `PsiDirectory.add(newClass)` — schreibt neue `.java`-Datei
3. `WriteCommandAction.runWriteCommandAction()` — modifiziert `extends`-Klausel aller betroffenen Klassen via `PsiJavaCodeReferenceElement`
4. Delegiert vollständig an `ExtractAndPullUpStrategy`

#### Strategie 4: `ExtractUtilityMethodStrategy`
1. `JavaPsiFacade.findClass()` — sucht bestehende Utility-Klasse; legt sie sonst neu an
2. `ExtractMethodProcessor` — extrahiert Methode
3. `MoveMembersProcessor` — verschiebt `PsiMethod` als `public static` in Utility-Klasse
4. `JavaCodeStyleManager.shortenClassReferences()` — optimiert Importe

---

### `DuplicateCodePanel` — UI-Integration

- Erbt **nicht** von `AbstractRefactoringPanel` (eigenständige Implementierung wegen tabellenbasiertem statt baumbasiertem Layout)
- Erkennung läuft in `Task.Backgroundable`; Rückschreiben in den EDT via `SwingUtilities.invokeLater()`
- Editor-Highlighting: `MarkupModel.addRangeHighlighter()` mit `EditorColors.SEARCH_RESULT_ATTRIBUTES`
- Cache-Invalidierung: Vergleich von `PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT`
- `TableSpeedSearch` aktiviert Schnellsuche im `JBTable`

### Erweiterung von `RefactoringsToolWindowFactory`
Fünfter Tab **Duplicate Code** wurde durch Addition einer `DuplicateCodePanel`-Instanz in `createToolWindowContent()` integriert — ohne strukturelle Änderung der bestehenden Tab-Logik.

---

## Datenmodell: Neue Klassen

### `DuplicateCodeFragment`
Repräsentiert eine einzelne Kloninstanz:
- `PsiFile file` — enthaltende Datei
- `int startLine`, `int endLine` — Zeilengrenzen
- `int tokenCount` — Tokenzahl
- `String codeText` — Quelltext-Snippet
- `PsiElement[] statements` — PSI-Statements (nach `DuplicateRangeAdjuster`)

### `DuplicateCodeGroup`
Gruppiert alle Instanzen eines Klons:
- `List<DuplicateCodeFragment> fragments`
- `int getSeverity()` — `tokenCount x fragments.size()`
- Klassifizierung: Intra-Class / Cross-Class / Cross-File

---

## Testinfrastruktur

| Testklasse | Scope | Status |
|---|---|---|
| `DuplicateCodeTest` | `DuplicateCodeGroup`, `PMDDuplicateCodeDetector`, `DuplicateCodeValidator`, `DuplicateRangeAdjuster`, Strategie-Analyse | **Neu** |
| `FeatureEnvyTest` | Move-Method-Kandidatenerkennung | Original |
| `GodClassTest` | Extract-Class-Kandidatenerkennung | Original |
| `TypeStateCheckingTest` | Type/State-Checking-Erkennung | Original |
| `GodClassDistanceMatrixTest` | Distanzmatrix-Berechnung | Original |

**Testrahmen:** `LightJavaCodeInsightFixtureTestCase` (In-Memory-PSI, kein laufendes IDE-Fenster erforderlich)  
**Testdaten:** `src/test/resources/testdata/`  
**Mock-JDK:** `src/test/resources/mockJDK-1.8/`

---

## Plugin-Registrierung (`plugin.xml`)

```xml
<!-- Tool Window -->
<toolWindow id="IntelliJDeodorant" anchor="bottom"
    factoryClass="...RefactoringsToolWindowFactory"/>

<!-- Error Handler -->
<errorHandler implementation="...GitHubErrorReporter"/>

<!-- Action: Duplicate Code -->
<action id="IntelliJDeodorant.DetectDuplicateCode"
        class="...DetectDuplicateCodeAction"
        text="Detect Duplicate Code">
  <!-- eingebettet in Gruppe IntelliJDeodorant.TopLevelGroup -> ToolsMenu -->
</action>
```

---

## Threading-Modell

| Operation | Thread | API |
|---|---|---|
| PSI lesen | Read-Thread | `ReadAction.compute()` |
| PSI schreiben | EDT Write-Thread | `WriteCommandAction.runWriteCommandAction()` |
| Langläufige Analyse | Background-Thread | `Task.Backgroundable` |
| UI-Update | EDT | `ApplicationManager.getApplication().invokeLater()` |

Alle direkten PSI-Zugriffe sind mit `ReadAction` abgesichert. Refactoring-Prozessoren werden ausschließlich im EDT ausgeführt.

---

## Future Work

### Data Class Detection mit PMD

#### Data Class — Definition und Heuristiken

Eine *Data Class* ist eine Klasse, die primär Daten kapselt und kaum Verhalten besitzt. Typische Merkmale: hoher Anteil an Getter/Setter-Methoden, wenige oder keine Methoden mit eigentlicher Geschäftslogik, fehlende Datenkapselung. Heuristiken zur Erkennung: Field-to-Method Ratio, Getter/Setter-Anteil an der Gesamtmethodenzahl, Abwesenheit von Methoden mit Kontrollfluss.

#### Implementierung des Data Class Detectors

**Field-to-Method Ratio Analyse**  
Verhältnis `Felder / Methoden` als primärer Indikator. Hohe Ratio (> 0,7) signalisiert Data Class. PMD oder direkter PSI-Walk via `PsiClass.getFields()` / `PsiClass.getMethods()`.

**Getter/Setter-Erkennung**  
Methoden mit Präfix `get`, `set`, `is` und einfachem Feldlesezugriff im Body werden identifiziert via `PsiMethodBody`-Analyse. Verhältnis Getter+Setter zu Gesamtmethoden als zweite Metrik.

**Behavioral Method Detection**  
Methoden mit mindestens einem `if`, `for`, `while`, `switch` oder Methodenaufruf auf externen Klassen gelten als Verhaltensträger. Deren Fehlen erhöht den Data-Class-Score.

#### Refactoring-Vorschläge für Data Classes

| Refactoring | Voraussetzung | PSI-API |
|---|---|---|
| **Encapsulate Field** | Öffentliches Feld ohne Getter/Setter | `PsiField` → `private` + Generierung via `GenerateMembersUtil` |
| **Move Method** | Methode greift primär auf andere Klasse zu | `MoveInstanceMethodProcessor` |
| **Extract Class** | Kohäsion in Feldergruppen nachweisbar | `ExtractClassProcessor` |

#### Integration in die bestehende Architektur

- Neuer Tab **Data Class** in `RefactoringsToolWindowFactory` analog zu den bestehenden Tabs
- Erkennungslogik in `core/dataclass/` (neues Paket), Fassadenaufruf via `JDeodorantFacade`
- Eintrag in `plugin.xml` nicht erforderlich (Tool-Window-Tab genügt)

---

### Hybride UX mit Echtzeit-Analyse

#### Echtzeit-Code-Analyse-Engine

**Hintergrund-Analyse-Threads**  
Permanente Analyse im Hintergrund via `BackgroundTaskQueue` oder `DaemonCodeAnalyzer`. Trigger: `PsiTreeChangeListener` bei Dokumentänderungen. Ergebnisse werden in einem projekt-weiten Cache (`UserDataHolder` auf `Project`) gehalten.

**Incremental Parsing und AST-Updates**  
Nur geänderte Dateien werden neu analysiert. Änderungsmarkierung via `PsiModificationTracker`-Deltas. Vollständiger Re-Scan nur bei Scope-Wechsel oder manuellem Refresh.

#### Code-Markierungssystem

**IntelliJ Editor-Markierungen**  
Markierungen werden als `RangeHighlighter` via `MarkupModel.addRangeHighlighter()` in der `EditorMarkupModel` gesetzt. Lebenszyklus-Verwaltung: Highlighter werden bei Dateiänderung entfernt und neu gesetzt.

**Farbcodierung und Severity-Levels**

| Severity | Farbe | Schwellwert |
|---|---|---|
| Critical | Rot | Score > 80 |
| Major | Orange | Score 50–80 |
| Minor | Gelb | Score 20–50 |
| Info | Grau | Score < 20 |

#### Light Bulb Integration

**Intention-Action-Implementierung**  
Registrierung als `IntentionAction` in `plugin.xml` unter `<intentionAction>`. `isAvailable()` prüft, ob der Cursor im Bereich eines erkannten Smells liegt. `invoke()` startet den Refactoring-Handler.

**Kontext-sensitive Refactoring-Vorschläge**  
`PsiElement` am Cursor-Offset wird analysiert; der passende Smell-Handler wird über eine Registry (`Map<SmellType, RefactoringHandler>`) aufgelöst.

#### Alt+Enter Refactoring-Menü

**Quick-Fix-Framework-Integration**  
Implementierung als `LocalInspectionTool` + `LocalQuickFix`. `LocalInspectionTool.checkFile()` liefert `ProblemDescriptor`-Instanzen; jeder Descriptor trägt einen `LocalQuickFix`, der den Refactoring-Handler aufruft.

**Refactoring-Vorschlags-Priorisierung**  
Fixes werden nach Severity absteigend sortiert. Implementierung via `ProblemDescriptor.getHighlightType()` und benutzerdefiniertem Comparator in der Inspection-Ergebnisliste.

---

### Systemintegration und Gesamtarchitektur

#### Plugin-Komponenten-Integration

Alle neuen Komponenten (Data Class Detector, Echtzeit-Engine, Intention Actions, Inspections) werden als `ProjectService` oder `ApplicationService` in `plugin.xml` registriert und via `ServiceManager.getService()` aufgelöst. Abhängigkeiten zwischen Komponenten werden ausschließlich über Interfaces entkoppelt.

#### Datenfluss und Ereignisbehandlung

```
PsiTreeChangeListener
       |
       v
IncrementalAnalysisQueue (Background)
       |
       v
SmellDetectionEngine (pro Datei)
       |
       v
SmellResultCache (Project-scoped UserData)
       |
       +--> MarkupModel (Editor-Highlighting)
       +--> DaemonCodeAnalyzer (Inspection-Markierungen)
       +--> ToolWindow (Tabellenaktualisierung)
```

#### Konfigurationsmanagement

**Benutzereinstellungen**  
Persistierung via `PersistentStateComponent<T>` als `@State`-annotierter `ApplicationService`. Einstellungsseite als `Configurable`-Implementierung unter *Settings → Tools → IntelliJDeodorant*.

**Regelkonfiguration**  
Schwellwerte (Token-Minimum, Ratio-Grenzwerte, Severity-Stufen) werden in einem `IntelliJDeodorantSettings`-Bean gespeichert und in der Analyse-Engine injiziert. Änderungen triggern automatische Cache-Invalidierung.

#### Performance-Optimierung

**Caching-Strategien**  
Mehrstufiger Cache: Datei-Level (invalidiert bei Dateiänderung), Projekt-Level (invalidiert bei Scope-Änderung). Cache-Keys: `VirtualFile` + `PsiModificationTracker.getModificationCount()`.

**Lazy Evaluation**  
Schwere Analysen (PDG-Slicing, Distanzmatrix) werden nur auf expliziten Nutzer-Trigger oder bei Sichtbarkeit im Tool-Window ausgeführt. Leichtgewichtige Heuristiken (Field-Ratio, Getter-Erkennung) laufen kontinuierlich im Hintergrund.

**Hintergrundverarbeitung**  
Alle Analysen laufen in `ReadAction.nonBlocking()` mit `CancellationToken`-Unterstützung. Laufende Analysen werden bei neuer Nutzerinteraktion abgebrochen und neu gestartet, um EDT-Blockierung zu vermeiden.
