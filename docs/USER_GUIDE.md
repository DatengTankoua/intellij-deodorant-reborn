# Benutzerdokumentation

**Plugin:** IntelliJDeodorant  
**Version:** 2025.2-COMPATIBLE-1.0  
**Zielgruppe:** Java-Entwickler, Software-Architekten  
**Kompatibilität:** IntelliJ IDEA 2021.3 bis 2025.2.x

---

## Voraussetzungen

- IntelliJ IDEA 2021.3 oder neuer (Community oder Ultimate)
- Java-Projekt geladen
- Plugin installiert: *Settings → Plugins → Install from Disk* (.zip-Datei)

---

## Nutzungsworkflow

### 1. Bestehende Code-Smell-Erkennung (Original-Funktionen)

1. **Tool Window öffnen:** *View → Tool Windows → IntelliJDeodorant* (alternativ: untere Leiste)
2. Tab wählen: **Feature Envy**, **Long Method**, **God Class** oder **Type/State Checking**
3. Analysebereich im Dropdown festlegen (Projekt / Modul / Paket / Datei)
4. **Refresh** klicken — Erkennung läuft im Hintergrund
5. Kandidaten selektieren und **Refactor** klicken

| Tab | Erkannter Smell | Refactoring |
|---|---|---|
| Feature Envy | Methode nutzt primär fremde Klasse | Move Method |
| Long Method | Methode zu komplex (PDG-Slicing) | Extract Method |
| God Class | Klasse hat zu viele Verantwortlichkeiten | Extract Class |
| Type/State Checking | Typ-Abfragen statt Polymorphismus | Replace Conditional with Polymorphism / Replace Type Code with State/Strategy |

---

### 2. Duplicate-Code-Erkennung und Refactoring (Neue Funktion)

**Einstieg über das Tools-Menü:**
*Tools → IntelliJDeodorant → Detect Duplicate Code*

Alternativ direkt über das Tool Window: Tab **Duplicate Code** wählen.

---

#### Schritt-für-Schritt-Workflow

```
[1] Refresh klicken
     |
     v
PMD CPD scannt alle Java-Quelldateien im gewählten Scope
- Minimale Tokenzahl: 60
- Bezeichner und Literale werden normalisiert (ignoriert)
     |
     v
[2] Validierungs-Pipeline (automatisch)
- DuplicateRangeAdjuster: PMD-Zeilen -> PSI-Statement-Grenzen
- ExtractMethodFeasibilityChecker: Fragmente < 7 Zeilen und nicht
  extrahierbare Fragmente werden verworfen
- DuplicateSimilarityChecker: strukturelle Typ-1/Typ-2-Gleichheit
  wird verifiziert
     |
     v
[3] Ergebnistabelle
- Spalten: Occurrences | Tokens | Avg Lines | Severity | Type | Locations
- Sortierung: absteigend nach Severity (= Tokens x Occurrences)
- Klick auf Zeile: hebt Duplikat im Editor farblich hervor
- Tooltip auf Locations-Spalte: vollständige Liste aller Fundstellen
     |
     v
[4] Eintrag selektieren -> Refactor klicken
     |
     v
[5] Automatische Strategieauswahl
     |
     +-- Gleiche Klasse -----------------> Strategie 1: Extract Method
     |
     +-- Verschiedene Klassen,
     |   Superklasse im Projektcode -----> Strategie 2: Extract + Pull Up
     |
     +-- Verschiedene Klassen,
     |   Superklasse extern (z.B. Object) -> Strategie 3: Extract Superclass
     |                                      (neue Zwischenklasse)
     |
     +-- Keine Hierarchiebeziehung ------> Strategie 4: Extract Utility Method
                                           (statische Methode in Utility-Klasse)
     |
     v
[6] Bestätigungsdialog: gewählte Strategie + betroffene Klassen anzeigen
     |
     v
[7] IntelliJ-Refactoring-Dialog (Methodenname, Sichtbarkeit eingeben)
     |
     v
[8] Refactoring wird auf alle Duplikate angewendet
```

---

#### Ergebnistabelle — Spaltenübersicht

| Spalte | Bedeutung |
|---|---|
| Occurrences | Anzahl der Kloninstanzen |
| Tokens | Tokenzahl der Klonsequenz |
| Avg Lines | Durchschnittliche Zeilenlänge der Fragmente |
| Severity | `Tokens × Occurrences` — Priorisierungsmetrik |
| Type | Intra-Class / Cross-Class / Cross-File |
| Locations | Erste Fundstelle (Tooltip zeigt alle) |

---

#### Die vier Refactoring-Strategien im Detail

**Strategie 1: Extract Method** (gleiche Klasse)  
Ein ExtractMethodProcessor extrahiert die redundante Sequenz in eine neue Methode. IntelliJ erkennt und ersetzt automatisch alle weiteren Duplikate innerhalb derselben Klasse.

**Strategie 2: Extract and Pull Up** (Superklasse im Projektcode editierbar)  
Die Methode wird zunächst in einer der betroffenen Klassen extracted, dann via PullUpProcessor in die gemeinsame Superklasse verschoben. Sichtbarkeit wird auf protected gesetzt.

**Strategie 3: Extract Superclass** (nur externe Superklasse vorhanden, z.B. Object)  
Es wird eine neue Zwischenklasse im Projektquellcode angelegt, die die externe Klasse erweitert. Alle betroffenen Klassen werden auf diese neue Klasse umgeparentet. Anschließend läuft Strategie 2.  
Eingabedialoge: Name der neuen Klasse, Zielverzeichnis.

**Strategie 4: Extract Utility Method** (keine gemeinsame Hierarchie)  
Die Methode wird extracted und anschließend via MoveMembersProcessor als public static in eine Utility-Klasse verschoben.  
Eingabedialoge: Name der Utility-Klasse, Zielverzeichnis.

---

### 3. Ergebnisse exportieren

Schaltfläche **Export** im Duplicate-Code-Tab: erzeugt eine CSV-Datei mit allen Duplikatgruppen inkl. Severity, Tokenzahl und Fundstellen.

---

## Hinweise

- Die Analyse läuft als Background-Task und blockiert den Editor nicht.
- Ergebnisse werden zwischengespeichert (Cache wird bei PSI-Änderungen automatisch invalidiert).
- Fragmente unter 7 Zeilen werden nicht als refactoring-würdig gewertet.
- Der Schwellwert von 60 Tokens ist auf moderate Kodebasen ausgelegt; bei sehr großen Projekten kann ein höherer Wert False Positives reduzieren.
