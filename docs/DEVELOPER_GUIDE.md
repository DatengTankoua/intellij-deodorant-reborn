# Entwickler-Handbuch: IntelliJDeodorant

**Version:** 2025.2-COMPATIBLE-1.0  
**Zielgruppe:** Plugin-Entwickler, Contributors, Maintainer  
**Voraussetzungen:** Java 11+, IntelliJ Platform SDK Kenntnisse  
**Letzte Aktualisierung:** 21. Januar 2026

---

## Inhaltsverzeichnis

1. [Entwicklungsumgebung einrichten](#entwicklungsumgebung-einrichten)
2. [Architektur-Überblick](#architektur-überblick)
3. [Code-Konventionen](#code-konventionen)
4. [Testing-Guidelines](#testing-guidelines)
5. [Neue Features implementieren](#neue-features-implementieren)
6. [Debugging](#debugging)
7. [Contributing Guidelines](#contributing-guidelines)
8. [API-Referenz](#api-referenz)

---

## Entwicklungsumgebung einrichten

### Voraussetzungen

```
   - IntelliJ IDEA 2021.3+ (Community oder Ultimate)
   - Java Development Kit 11 (für Build)
   - Java Development Kit 21 (optional, für Development)
   - Git
   - Gradle 8.5+ (wird automatisch via Wrapper installiert)
```

### Repository klonen

```bash
# HTTPS
git clone https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis.git

# SSH
git clone git@gitlab.uni-marburg.de:datengta/intellijdeodorant-thesis.git

cd intellijdeodorant
```

### Projekt in IntelliJ öffnen

1. **IntelliJ IDEA starten**

2. **Projekt öffnen:**
   - `File` → `Open`
   - Wählen Sie `build.gradle` im Projekt-Root
   - Wählen Sie "Open as Project"

3. **Gradle Sync durchführen:**
   - IntelliJ erkennt automatisch Gradle-Projekt
   - Wartet, bis Sync abgeschlossen ist (~2 Minuten beim ersten Mal)

4. **SDK konfigurieren:**
   - `File` → `Project Structure` → `Project`
   - Project SDK: Java 11
   - Project Language Level: 11

### Build durchführen

```bash
# Clean Build
./gradlew clean build

# Nur kompilieren (ohne Tests)
./gradlew build -x test

# Tests ausführen
./gradlew test

# Plugin bauen
./gradlew buildPlugin

# Plugin in IDE testen
./gradlew runIde
```

**Erwartetes Ergebnis:**
```
BUILD SUCCESSFUL in 42s
12 actionable tasks: 12 executed
```

### Erste Änderungen testen

1. **Änderung machen:**
   - Editieren Sie z.B. `JDeodorantFacade.java`
   - Fügen Sie Kommentar hinzu

2. **Plugin testen:**
   ```bash
   ./gradlew runIde
   ```
   - Neue IntelliJ-Instanz startet (mit Plugin)
   - Öffnen Sie ein Test-Projekt
   - Testen Sie Plugin-Funktionalität

3. **Tests durchführen:**
   ```bash
   ./gradlew test
   ```

---

## Architektur-Überblick

### High-Level-Architektur

```
┌─────────────────────────────────────────────────────┐
│                  UI Layer (IntelliJ)                │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ Tool Windows │  │ Table Models │  │  Dialogs  │  │
│  └──────────────┘  └──────────────┘  └───────────┘  │
└────────────────────────┬────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│             Refactoring Layer (IDE)                 │
│  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ RefactoringTypes │  │ RefactoringsApplier      │ │
│  └──────────────────┘  └──────────────────────────┘ │
└────────────────────────┬────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│              Facade Layer (API)                     │
│  ┌──────────────────────────────────────────────┐   │
│  │         JDeodorantFacade                     │   │
│  │  - getMoveMethodOpportunities()              │   │
│  │  - getExtractClassOpportunities()            │   │
│  │  - getExtractMethodOpportunities()           │   │
│  │  - getTypeCheckEliminationOpportunities()    │   │
│  └──────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│            Analysis Engine (Core)                   │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────┐    │
│  │ ASTReader│  │ SystemObj│  │ DistanceMatrix  │    │
│  └──────────┘  └──────────┘  └─────────────────┘    │
│  ┌──────────────────────────────────────────────┐   │
│  │  Metrics: Entity Placement, Distance, etc.   │   │
│  └──────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│              IntelliJ Platform                      │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────┐    │
│  │   PSI    │  │   VFS    │  │  Refactoring    │    │
│  │  (Code)  │  │ (Files)  │  │     API         │    │
│  └──────────┘  └──────────┘  └─────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### Package-Struktur

```
org.jetbrains.research.intellijdeodorant/
│
├── JDeodorantFacade.java           # Haupt-API Entry Point
├── IntelliJDeodorantBundle.java    # I18n Resource Bundle
│
├── core/                           # Analyse-Engine (Algorithmen)
│   ├── ast/                        # AST-Parsing und Analyse
│   │   ├── ASTReader.java          # PSI → JDeodorant AST Konverter
│   │   ├── ClassObject.java        # Klassen-Repräsentation
│   │   ├── MethodObject.java       # Methoden-Repräsentation
│   │   ├── SystemObject.java       # Projekt-Repräsentation
│   │   └── decomposition/          # Code-Dekomposition
│   │       ├── cfg/                # Control Flow Graphen
│   │       └── matching/           # AST-Pattern-Matching
│   │
│   └── distance/                   # Metrik-Berechnungen
│       ├── DistanceMatrix.java     # Distanz-Metriken
│       ├── EntityPlacement.java    # Entity Placement Algorithmus
│       └── ProjectInfo.java        # Projekt-Informationen
│
├── ide/                            # IDE-Integration
│   ├── fus/                        # Feature Usage Statistics (No-Op)
│   │   ├── IntelliJDeodorantLogger.java
│   │   └── collectors/
│   │       └── IntelliJDeodorantCounterCollector.java
│   │
│   ├── refactoring/                # Refactoring-Logik
│   │   ├── RefactoringType.java    # Abstrakte Basis-Klasse
│   │   ├── RefactoringsApplier.java # Wendet Refactorings an
│   │   │
│   │   ├── moveMethod/             # Feature Envy → Move Method
│   │   │   ├── MoveMethodRefactoringType.java
│   │   │   └── MoveMethodRefactoring.java
│   │   │
│   │   ├── extractClass/           # God Class → Extract Class
│   │   │   ├── ExtractClassRefactoringType.java
│   │   │   └── ExtractClassRefactoring.java
│   │   │
│   │   ├── extractMethod/          # Long Method → Extract Method
│   │   │   └── ExtractMethodRefactoringType.java
│   │   │
│   │   └── typeStateChecking/      # Type Checking → Polymorphism
│   │       ├── TypeCheckRefactoringType.java
│   │       ├── TypeCheckElimination.java
│   │       ├── TypeCheckEliminationGroup.java
│   │       └── PolymorphismRefactoring.java
│   │
│   └── ui/                         # UI-Komponenten
│       ├── AbstractRefactoringPanel.java    # Basis-Panel
│       ├── MoveMethodPanel.java             # Feature Envy UI
│       ├── GodClassPanel.java               # God Class UI
│       ├── ExtractMethodPanel.java          # Long Method UI
│       ├── TypeCheckingPanel.java           # Type Checking UI
│       │
│       └── table-models/           # Table Models für UI
│           ├── MoveMethodTableModel.java
│           ├── ExtractMethodTreeTableModel.java
│           ├── GodClassTreeTableModel.java
│           └── TypeCheckingTreeTableModel.java
│
├── inheritance/                    # Vererbungs-Analyse
│   └── InheritanceTree.java        # Klassen-Hierarchien
│
├── reporting/                      # Issue-Reporting
│   └── GithubErrorReporter.java    # Bug-Reports zu GitHub
│
└── utils/                          # Hilfsfunktionen
    ├── PsiUtils.java               # PSI-Helper-Methoden
    └── math/                       # Mathematische Utilities
```

### Daten-Fluss: Feature Envy Detection

```
┌────────────────────────────────────────────────────┐
│ 1. User: "Tools → Detect Feature Envy"             │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│ 2. MoveMethodPanel.calculateRefactorings()         │
│    - Startet Background Task                       │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓ (Background Thread)
┌────────────────────────────────────────────────────┐
│ 3. JDeodorantFacade.getMoveMethod...()             │
│    - Entry Point für Algorithmus                   │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│ 4. ASTReader.readProject()                         │
│    - PSI → JDeodorant AST                          │
│    - Erstellt SystemObject                         │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│ 5. SystemObject.generateMoveMethodCandidates()     │
│    - Analysiert alle Methoden                      │
│    - Berechnet Access-Metriken                     │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│ 6. DistanceMatrix.calculateDistances()             │
│    - Feature Envy Score berechnen                  │
│    - Filter: Target >> Source                      │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓ (zurück zu EDT)
┌────────────────────────────────────────────────────┐
│ 7. MoveMethodPanel.displayResults()                │
│    - invokeLater() für UI-Update                   │
│    - Tabelle befüllen                              │
└────────────────────────────────────────────────────┘
```

---

## Code-Konventionen

### Java-Code-Stil

**Naming:**
```java
// Klassen: PascalCase
public class MoveMethodRefactoring { }

// Methoden: camelCase
public void calculateDistance() { }

// Konstanten: UPPER_SNAKE_CASE
public static final int MAX_DISTANCE = 100;

// Private Fields: camelCase mit _
private DistanceMatrix distanceMatrix;

// Packages: lowercase
package org.jetbrains.research.intellijdeodorant.core.ast;
```

**Threading:**
```java
// PSI-Zugriffe IMMER in ReadAction
ReadAction.run(() -> {
    String name = psiClass.getQualifiedName();
});

// Mit Rückgabewert: ReadAction.compute()
String name = ReadAction.compute(() -> 
    psiClass.getQualifiedName()
);

// UI-Updates IMMER in EDT
ApplicationManager.getApplication().invokeLater(() -> {
    tableModel.fireTableDataChanged();
});

// Background-Tasks für lange Operationen
ProgressManager.getInstance().run(
    new Task.Backgroundable(project, "Analyzing...") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
            // Rechenintensive Arbeit hier
        }
    }
);
```

**Kommentare:**
```java
/**
 * MODERNISIERT: ReadAction.compute() statt ApplicationManager
 * 
 * Diese Methode berechnet die Distanz zwischen zwei Methoden
 * basierend auf gemeinsam genutzten Attributen.
 * 
 * @param source Quell-Methode
 * @param target Ziel-Methode
 * @return Distanz-Wert (0.0 - 1.0)
 */
public double calculateDistance(MethodObject source, MethodObject target) {
    return ReadAction.compute(() -> {
        // Implementation
    });
}
```

**Error Handling:**
```java
// Checked Exceptions loggen und weitergeben
try {
    psiClass.delete();
} catch (IncorrectOperationException e) {
    Logger.getInstance(getClass()).error("Cannot delete class", e);
    throw e;
}

// Null-Checks
@Nullable
public PsiClass findClass(String name) {
    return ReadAction.compute(() -> 
        JavaPsiFacade.getInstance(project).findClass(name, scope)
    );
}

@NotNull
public List<PsiMethod> getMethods() {
    // Niemals null zurückgeben
    return Collections.emptyList();
}
```

### Code-Review-Checkliste

Vor jedem Merge Request prüfen:

- [ ] **Threading:** Alle PSI-Zugriffe in ReadAction?
- [ ] **EDT:** UI-Updates in invokeLater()?
- [ ] **Null-Checks:** @Nullable/@NotNull korrekt verwendet?
- [ ] **Error-Handling:** Try-Catch mit Logging?
- [ ] **Tests:** Neue Funktionalität getestet?
- [ ] **Kommentare:** Komplexe Logik dokumentiert?
- [ ] **Performance:** Keine blocking Operationen im EDT?
- [ ] **Code-Stil:** Entspricht Konventionen?

---

## Testing-Guidelines

### Unit-Tests

**Datei-Konvention:**
```
Klasse:      MoveMethodRefactoring.java
Test-Datei:  MoveMethodRefactoringTest.java (im test/java/ Verzeichnis)
```

**Test-Struktur:**
```java
public class FeatureEnvyTest {
    private Project project;
    private PsiClass testClass;
    
    @BeforeEach
    void setUp() {
        // Test-Setup
        project = createTestProject();
        testClass = createTestClass();
    }
    
    @Test
    void testFeatureEnvyDetection() {
        // Given
        MethodObject method = createMethodWithFeatureEnvy();
        
        // When
        Set<MoveMethodCandidate> candidates = 
            JDeodorantFacade.getMoveMethodRefactoringOpportunities(
                projectInfo, 
                progressIndicator
            );
        
        // Then
        assertThat(candidates, hasSize(greaterThan(0)));
        assertThat(candidates.iterator().next().getMethod(), 
                   equalTo(method.getPsiMethod()));
    }
    
    @AfterEach
    void tearDown() {
        // Cleanup
        disposeProject(project);
    }
}
```

**Mocking PSI-Elemente:**
```java
// Verwenden Sie IntelliJ's Light Test Framework
public class GodClassTest extends BasePlatformTestCase {
    
    @Test
    void testGodClassDetection() {
        // Given: Erstelle Test-Datei im virtuellen Filesystem
        PsiFile psiFile = createTestFile(
            "GodClass.java",
            "public class GodClass {\n" +
            "  // 50+ Methoden hier\n" +
            "}"
        );
        
        // When: Analyse durchführen
        Set<ExtractClassCandidate> candidates = 
            JDeodorantFacade.getExtractClassRefactoringOpportunities(...);
        
        // Then: Validierung
        assertTrue(candidates.size() > 0);
    }
}
```

### Integration-Tests

**Test-Projekte verwenden:**
```java
public class RefactoringIntegrationTest {
    
    @Test
    void testMoveMethodRefactoringOnRealProject() {
        // Given: Lade echtes Test-Projekt
        Project project = loadProject("testdata/FeatureEnvyProject");
        
        // When: Führe vollständige Analyse durch
        Set<MoveMethodCandidate> candidates = 
            JDeodorantFacade.getMoveMethodRefactoringOpportunities(...);
        
        // Then: Validiere Ergebnisse
        assertEquals(3, candidates.size());
        
        // When: Wende Refactoring an
        RefactoringsApplier.moveRefactoring(candidates);
        
        // Then: Code sollte kompilierbar sein
        assertTrue(projectCompiles(project));
    }
}
```

### Performance-Tests

```java
@Test
void testPerformanceOnLargeProject() {
    // Given: Großes Projekt (500+ Klassen)
    Project project = loadProject("testdata/ApacheCommonsLang");
    
    // When: Messung der Analyse-Zeit
    long startTime = System.currentTimeMillis();
    Set<MoveMethodCandidate> candidates = 
        JDeodorantFacade.getMoveMethodRefactoringOpportunities(...);
    long duration = System.currentTimeMillis() - startTime;
    
    // Then: Performance-Anforderungen prüfen
    assertTrue("Analysis took " + duration + "ms", duration < 5000); // < 5s
    assertTrue("Found too few candidates", candidates.size() > 10);
}
```

### Test-Daten

Test-Dateien liegen in:
```
src/test/resources/testdata/
├── core/
│   ├── ast/
│   │   └── testFieldAccesses/
│   │       └── testFieldAccesses.java
│   └── distance/
│       └── godclass/
│           ├── testAbstractMethod.java
│           └── testOnlyFields.java
└── ide/
    └── refactoring/
        └── godclass/
            └── TestAccessOfNewObject/
```

**Test-Datei erstellen:**
```java
// testdata/featureenvy/SimpleFeatureEnvy.java
public class Invoice {
    private Customer customer;
    
    // Feature Envy: Nutzt nur customer-Daten
    public String getCustomerInfo() {
        return customer.getName() + " " + customer.getEmail();
    }
}

public class Customer {
    private String name;
    private String email;
    
    public String getName() { return name; }
    public String getEmail() { return email; }
}
```

---

## Support für Entwickler

### Hilfe bei Problemen

1. **Prüfen Sie bestehende Issues:**
   - [GitLab Issues](https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis/-/issues)

2. **IntelliJ Platform Documentation:**
   - [Official SDK Docs](https://plugins.jetbrains.com/docs/intellij/)
   - [PSI Guide](https://plugins.jetbrains.com/docs/intellij/psi.html)
   - [Threading Rules](https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html)

3. **Community:**
   - IntelliJ Platform Slack
   - JetBrains Plugin Development Forum

4. **Erstellen Sie neues Issue:**
   - Titel: `[DEV] Problem description`
   - Inkludieren Sie:
     - IntelliJ SDK Version
     - Java Version
     - Schritte zur Reproduktion
     - Stack-Trace
     - Minimal reproducible example

---

**Entwickler-Handbuch Version:** 1.0  
**Letzte Aktualisierung:** 21. Januar 2026  
**Maintainer:** Dateng Tankoua Emery Josian  
**Kontakt:** datengtankoua@gmail.com

---

**Happy Coding!**
