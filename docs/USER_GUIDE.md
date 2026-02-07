# Benutzerhandbuch: IntelliJDeodorant

**Version:** 2025.2-COMPATIBLE-1.0  
**Zielgruppe:** Java-Entwickler, Software-Architekten, Studenten  
**IntelliJ IDEA:** 2021.3 bis 2025.2+  
**Letzte Aktualisierung:** 21. Januar 2026

---

## Inhaltsverzeichnis

1. [Über IntelliJDeodorant](#über-intellijdeodorant)
2. [Installation](#installation)
3. [Erste Schritte](#erste-schritte)
4. [Code-Smells Erkennen](#code-smells-erkennen)
5. [Refactorings Durchführen](#refactorings-durchführen)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)
8. [FAQ](#faq)

---

## Über IntelliJDeodorant

### Was ist IntelliJDeodorant?

IntelliJDeodorant ist ein Plugin für IntelliJ IDEA, das automatisch **Code-Smells** (Anzeichen für schlechtes Software-Design) in Ihrem Java-Code erkennt und **konkrete Refactoring-Vorschläge** macht.

### Unterstützte Code-Smells

| Code-Smell | Beschreibung | Refactoring |
|------------|--------------|-------------|
| **Feature Envy** | Eine Methode nutzt mehr Daten aus einer anderen Klasse als aus der eigenen | Move Method |
| **God Class** | Eine Klasse hat zu viele Verantwortlichkeiten | Extract Class |
| **Long Method** | Eine Methode ist zu lang und macht zu viel | Extract Method |
| **Type/State Checking** | Switch-Statements über Typen/Zustände | Replace Conditional with Polymorphism |

### Wissenschaftliche Grundlage

IntelliJDeodorant basiert auf dem **JDeodorant-Framework**, das auf peer-reviewed Forschungsarbeiten beruht:

- Tsantalis et al. (2011): Feature Envy Detection
- Fokaefs et al. (2012): God Class Detection
- Martin Fowler (2018): Refactoring-Patterns

---

## Installation

### Systemvoraussetzungen

- **IntelliJ IDEA:** Version 2021.3 oder neuer (Community oder Ultimate Edition)
- **Java Development Kit:** Version 11 oder höher
- **Betriebssystem:** Windows, macOS, oder Linux
- **Speicher:** Mindestens 2 GB freier RAM (4 GB empfohlen)

### Installationsmethoden

#### Methode 1: Aus GitLab Release (Empfohlen)

1. **Plugin herunterladen:**
   - Besuchen Sie: [GitLab Releases](https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis/-/releases)
   - Laden Sie `IntelliJDeodorant-2025.2-COMPATIBLE-1.0.zip` herunter

2. **IntelliJ IDEA öffnen:**
   - Starten Sie IntelliJ IDEA

3. **Plugin installieren:**
   - Menü: `File` → `Settings` (Windows/Linux) oder `IntelliJ IDEA` → `Preferences` (macOS)
   - Wählen Sie: `Plugins`
   - Klicken Sie auf das Zahnrad-Symbol ⚙️ oben rechts
   - Wählen Sie: `Install Plugin from Disk...`
   - Navigieren Sie zur heruntergeladenen ZIP-Datei
   - Klicken Sie: `OK`

4. **IntelliJ neu starten:**
   - Klicken Sie auf `Restart IDE`
   - Nach dem Neustart ist das Plugin aktiv

#### Methode 2: Aus Source Code bauen

```bash
# Repository klonen
git clone https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis.git
cd intellijdeodorant

# Plugin bauen
./gradlew buildPlugin

# Plugin finden unter:
# build/distributions/IntelliJDeodorant-2025.2-COMPATIBLE-1.0.zip

# Dann wie Methode 1 installieren
```

### Installation überprüfen

1. Öffnen Sie: `File` → `Settings` → `Plugins`
2. Suchen Sie nach "IntelliJDeodorant"
3. Status sollte sein: ✅ **Enabled**

**Hinweis:** Falls das Plugin nicht erscheint, starten Sie IntelliJ noch einmal neu.

---

## Erste Schritte

### 1. Projekt öffnen

Öffnen Sie ein Java-Projekt in IntelliJ IDEA:
- `File` → `Open` → Wählen Sie Ihr Projekt-Verzeichnis

**Empfohlen für erste Tests:**
- Eigenes Projekt mit mindestens 50 Java-Klassen
- Projekt Test (suite-2025) in dem Ordner test-projects
- Oder: Apache Commons Lang 3.12 (zum Testen)

### 2. Plugin-Menü finden

Das Plugin-Menü befindet sich unter:
```
Tools → IntelliJDeodorant
```

Sie sehen dort vier Optionen:
-  Detect Feature Envy
-  Detect God Class
-  Detect Long Method
-  Detect Type/State Checking

### 3. Erste Analyse durchführen

**Beispiel: Feature Envy erkennen**

1. Klicken Sie: `Tools` → `IntelliJDeodorant` → `Feature Envy` 

2. Ein neues Tool-Window öffnet sich (meist unten in IntelliJ)

3. Klicken Sie auf den **"Refresh"**-Button (🔄)

4. Warten Sie, während IntelliJ Ihr Projekt analysiert
   - Bei 500 Klassen: ca. 1-2 Sekunden
   - Eine Fortschrittsanzeige erscheint

5. **Ergebnisse werden angezeigt:**
   - Liste der gefundenen Feature Envy-Kandidaten
   - Jede Zeile zeigt: Methode → Zielklasse → Metriken

---

## Code-Smells Erkennen

### Feature Envy Detection

**Was wird erkannt:**
Methoden, die mehr Daten aus einer anderen Klasse nutzen als aus ihrer eigenen Klasse.

**Beispiel:**
```java
// FeatureEnvy.java
public class Invoice {
    private Customer customer;
    
    // FEATURE ENVY: Nutzt nur customer-Daten
    public String getCustomerFullInfo() {
        return customer.getFirstName() + " " + 
               customer.getLastName() + " " +
               customer.getEmail() + " " +
               customer.getPhoneNumber();
    }
}
```

**Wie erkennen:**

1. `Tools` → `IntelliJDeodorant` → `Feature Envy`
2. Klicken Sie "Refresh" 🔄
3. Ergebnisse zeigen:
   
   Methode                 | Move to      | Source/Target accessed members
   ---------------------------|-----------------|-------------------
   Invoice.getCustomerInfo()  | Customer        | 0/4      
   

**Metriken verstehen:**
- **Source Members:** Wie viele Attribute der eigenen Klasse werden genutzt
- **Target Members:** Wie viele Attribute der Zielklasse werden genutzt
- **Regel:** Target >> Source = Feature Envy

---

### God Class Detection

**Was wird erkannt:**
Klassen, die zu viele Verantwortlichkeiten haben und in mehrere Klassen aufgeteilt werden sollten.

**Beispiel:**
```java
// GodClass.java (BAD)
public class OrderManager {
    // Verantwortung 1: Order-Management
    public void createOrder() { }
    public void cancelOrder() { }
    
    // Verantwortung 2: Zahlungs-Verarbeitung
    public void processPayment() { }
    public void refundPayment() { }
    
    // Verantwortung 3: E-Mail-Versand
    public void sendConfirmationEmail() { }
    public void sendInvoiceEmail() { }
    
    // Verantwortung 4: Logging
    public void logOrderCreated() { }
    public void logPaymentProcessed() { }
    
    // ... 50 weitere Methoden
}
```

**Wie erkennen:**

1. `Tools` → `IntelliJDeodorant` → `God Class`

2. Klicken Sie "Refresh" 🔄

3. **Ergebnisse interpretieren:**
   
   Source Class/General concept         |Extractable concept | Source/Extracted accessed members
   ---------------|------------------------|---------------------------
   OrderManager | PaymentService  | 4/6                     
   OrderManager  | EmailNotifier | 2/4                     
   

**Was bedeuten die Vorschläge:**
- Plugin identifiziert zusammenhängende Methoden-Gruppen
- Schlägt neue Klassen vor (z.B. `PaymentService`)
- Zeigt, welche Methoden in neue Klasse verschoben werden sollten

---

### Long Method Detection

**Was wird erkannt:**
Methoden, die zu lang sind (typisch >20 Zeilen) und Code-Fragmente enthalten, die extrahiert werden können.

**Beispiel:**
```java
// LongMethod.java
public void processOrder(Order order) {
    // Fragment 1: Validierung (15 Zeilen)
    if (order == null) throw new IllegalArgumentException();
    if (order.getItems().isEmpty()) throw new IllegalStateException();
    // ... weitere Validierungen
    
    // Fragment 2: Preis-Berechnung (20 Zeilen)
    double total = 0;
    for (Item item : order.getItems()) {
        total += item.getPrice() * item.getQuantity();
        if (item.hasDiscount()) {
            total -= item.getDiscount();
        }
    }
    // ... weitere Berechnungen
    
    // Fragment 3: E-Mail-Versand (25 Zeilen)
    String emailBody = "Your order...";
    // ... E-Mail-Erstellung
    emailService.send(emailBody);
}
```

**Wie erkennen:**

1. `Tools` → `IntelliJDeodorant` → `Long Method`

2. Klicken Sie "Refresh" 🔄

3. **Ergebnisse zeigen Code-Fragmente:**
   
   Source method              | Variable name 
   ---------------------|-------
   processOrder()       | discount  
   processOrder()       | subTotal 
   

4. **Doppelklick auf Fragment:**
   - Zeigt Preview des zu extrahierenden Codes
   - Zeigt vorgeschlagenen Methodennamen
   - Zeigt benötigte Parameter

---

### Type/State Checking Detection

**Was wird erkannt:**
Switch-Statements oder If-Else-Ketten über Typen oder Zustände, die durch Polymorphismus ersetzt werden sollten.

**Beispiel:**
```java
// TypeChecking.java (BAD)
public class PaymentProcessor {
    enum PaymentType { CREDIT_CARD, PAYPAL, BANK_TRANSFER }
    
    public void processPayment(PaymentType type, double amount) {
        switch (type) {
            case CREDIT_CARD:
                // 20 Zeilen Code für Kreditkarte
                validateCardNumber();
                checkCardLimit();
                chargeCard(amount);
                break;
                
            case PAYPAL:
                // 20 Zeilen Code für PayPal
                redirectToPayPal();
                waitForConfirmation();
                break;
                
            case BANK_TRANSFER:
                // 20 Zeilen Code für Überweisung
                generateIBAN();
                sendInvoice();
                break;
        }
    }
}
```

**Wie erkennen:**

1. `Tools` → `IntelliJDeodorant` → `Type/State Checking`

2. Klicken Sie "Refresh" 🔄

3. **Ergebnisse:**
   
   Type Checking method | Refactoring type |System-level occurrences | Class-level occurrences | Avg. LOC/Case
   --------------------|-------------------|--------------|---------------|--------
   PaymentProcessor::processPayment()  | Replace Type Code with State/Strategy |3    | 3        | 2,21
   

4. **Refactoring-Vorschlag:**
   - Erstelle Interface `PaymentMethod`
   - Erstelle 3 Implementierungen:
     - `CreditCardPayment`
     - `PayPalPayment`
     - `BankTransferPayment`
   - Ersetze Switch durch Polymorphismus

---

## Refactorings Durchführen

### Refactoring-Prozess (Allgemein)

1. **Code-Smell erkennen** (siehe oben)
2. **Kandidat auswählen** (Doppelklick in Tabelle)
3. **Preview anschauen** (Dialog öffnet sich)
4. **Refactoring anwenden** (Button klicken)
5. **Überprüfen** (Code wurde automatisch geändert)

### Feature Envy → Move Method Refactoring

**Schritt-für-Schritt:**

1. **Feature Envy erkennen** (siehe oben)

2. **Kandidat auswählen:**
   - Doppelklick auf Zeile in Tabelle
   - Oder: Rechtsklick → "Do Refactoring"

3. **Move Method Dialog erscheint:**
   ```
   ┌─────────────────────────────────────────┐
   │ Move Instance Method                    │
   ├─────────────────────────────────────────┤
   │ Method: getCustomerInfo()               │
   │ From:   Invoice                         │
   │ To:     Customer                        │
   │                                         │
   │ Target Instance:                        │
   │  ○ customer (Field)                     │
   │  ○ customer (Parameter)                 │
   │                                         │
   │ [Preview] [Refactor] [Cancel]          │
   └─────────────────────────────────────────┘
   ```

4. **Konfiguration:**
   - **Target Instance:** Wählen Sie, wie auf Zielklasse zugegriffen wird
     - Field: Nutzt bestehendes Attribut
     - Parameter: Fügt Parameter hinzu

5. **Preview (Optional):**
   - Klicken Sie "Preview"
   - Sehen Sie Vorher/Nachher-Vergleich

6. **Refactoring anwenden:**
   - Klicken Sie "Refactor"
   - IntelliJ verschiebt die Methode automatisch
   - Alle Aufrufe werden aktualisiert

**Vorher:**
```java
// Invoice.java
public String getCustomerInfo() {
    return customer.getFirstName() + " " + customer.getLastName();
}

// Main.java
invoice.getCustomerInfo();
```

**Nachher:**
```java
// Customer.java (NEUE METHODE)
public String getInfo() {
    return getFirstName() + " " + getLastName();
}

// Main.java (ANGEPASST)
invoice.getCustomer().getInfo();
```

---

### God Class → Extract Class Refactoring

**Schritt-für-Schritt:**

1. **God Class erkennen** (siehe oben)

2. **Extract Class-Kandidat auswählen:**
   - Doppelklick auf Zeile

3. **Extract Class Dialog:**
   ```
   ┌─────────────────────────────────────────┐
   │ Extract Class                           │
   ├─────────────────────────────────────────┤
   │ Extracted Class Name: PaymentService   │
   │                                         │
   │ Methods to Extract:                     │
   │ ☑ processPayment()                     │
   │ ☑ refundPayment()                      │
   │ ☑ validatePayment()                    │
   │                                         │
   │ Fields to Extract:                      │
   │ ☑ paymentGateway                       │
   │ ☑ transactionLog                       │
   │                                         │
   │ [Extract] [Cancel]                     │
   └─────────────────────────────────────────┘
   ```

4. **Konfiguration:**
   - **Class Name:** Name der neuen Klasse (editierbar)
   - **Methods:** Methoden die verschoben werden
   - **Fields:** Attribute die verschoben werden

5. **Extract durchführen:**
   - Klicken Sie "Extract"
   - IntelliJ erstellt automatisch:
     - Neue Klasse `PaymentService`
     - Verschiebt Methoden und Felder
     - Erstellt Instanz in ursprünglicher Klasse
     - Delegiert Aufrufe

**Nachher:**
```java
// PaymentService.java (NEU)
public class PaymentService {
    private PaymentGateway paymentGateway;
    
    public void processPayment() { ... }
    public void refundPayment() { ... }
}

// OrderManager.java (VEREINFACHT)
public class OrderManager {
    private PaymentService paymentService = new PaymentService();
    
    public void createOrder() { 
        // ...
        paymentService.processPayment();
    }
}
```

---

### Long Method → Extract Method Refactoring

**Schritt-für-Schritt:**

1. **Long Method erkennen** (siehe oben)

2. **Code-Fragment auswählen:**
   - Doppelklick auf Fragment in Tabelle

3. **Extract Method Preview:**
   ```
   ┌─────────────────────────────────────────┐
   │ Extract Method                          │
   ├─────────────────────────────────────────┤
   │ Method Name: calculateTotal            │
   │                                         │
   │ Code to extract:                        │
   │ ┌───────────────────────────────────┐  │
   │ │ double total = 0;                 │  │
   │ │ for (Item item : order.getItems()){│  │
   │ │     total += item.getPrice() * ..  │  │
   │ │ }                                  │  │
   │ │ return total;                      │  │
   │ └───────────────────────────────────┘  │
   │                                         │
   │ Parameters:                             │
   │ - Order order                           │
   │                                         │
   │ [Extract] [Cancel]                     │
   └─────────────────────────────────────────┘
   ```

4. **Methodennamen anpassen:**
   - Editieren Sie "Method Name" nach Bedarf

5. **Extract durchführen:**
   - Klicken Sie "Extract"
   - Neue Methode wird erstellt
   - Original-Code wird durch Methoden-Aufruf ersetzt

**Vorher:**
```java
public void processOrder(Order order) {
    // 20 Zeilen Preis-Berechnung
    double total = 0;
    for (Item item : order.getItems()) {
        total += item.getPrice() * item.getQuantity();
    }
    // ... weitere Logik
}
```

**Nachher:**
```java
public void processOrder(Order order) {
    double total = calculateTotal(order);
    // ... weitere Logik
}

private double calculateTotal(Order order) {
    double total = 0;
    for (Item item : order.getItems()) {
        total += item.getPrice() * item.getQuantity();
    }
    return total;
}
```

---

### Type Checking → Replace Conditional with Polymorphism

**Schritt-für-Schritt:**

1. **Type Checking erkennen** (siehe oben)

2. **Kandidat auswählen:**
   - Doppelklick auf Zeile

3. **Refactoring-Strategie-Dialog:**
   ```
   ┌─────────────────────────────────────────┐
   │ Replace Conditional with Polymorphism   │
   ├─────────────────────────────────────────┤
   │ Strategy:                               │
   │ ○ Replace Type Code with State/Strategy│
   │ ○ Replace Conditional with Polymorphism│
   │                                         │
   │ Interface Name: PaymentMethod          │
   │                                         │
   │ Implementations:                        │
   │ - CreditCardPayment                    │
   │ - PayPalPayment                        │
   │ - BankTransferPayment                  │
   │                                         │
   │ [Apply] [Cancel]                       │
   └─────────────────────────────────────────┘
   ```

4. **Apply Refactoring:**
   - Klicken Sie "Apply"
   - IntelliJ erstellt automatisch:
     - Interface `PaymentMethod`
     - 3 Implementierungen
     - Ersetzt Switch durch Polymorphismus

**Vorher:**
```java
public void processPayment(PaymentType type, double amount) {
    switch (type) {
        case CREDIT_CARD:
            // Kreditkarten-Logik
            break;
        case PAYPAL:
            // PayPal-Logik
            break;
    }
}
```

**Nachher:**
```java
// PaymentMethod.java (NEU)
public interface PaymentMethod {
    void process(double amount);
}

// CreditCardPayment.java (NEU)
public class CreditCardPayment implements PaymentMethod {
    @Override
    public void process(double amount) {
        // Kreditkarten-Logik
    }
}

// PayPalPayment.java (NEU)
public class PayPalPayment implements PaymentMethod {
    @Override
    public void process(double amount) {
        // PayPal-Logik
    }
}

// Verwendung (VEREINFACHT)
paymentMethod.process(amount);
```

---

## Best Practices

### Wann sollte man Code-Smells beheben?

**JA, beheben wenn:**
- ✅ Code wird häufig geändert (hohe Change-Rate)
- ✅ Bugs treten häufig in dieser Klasse/Methode auf
- ✅ Code ist schwer zu verstehen
- ✅ Tests sind schwer zu schreiben
- ✅ Neues Feature wird hinzugefügt

**NEIN, nicht beheben wenn:**
- ❌ Code ist Legacy und wird nie geändert
- ❌ Code funktioniert stabil seit Jahren
- ❌ Zeitdruck im Projekt
- ❌ Risiko von Regression ist hoch
- ❌ Team hat keine Test-Coverage

### Performance-Tipps

**Große Projekte (>1000 Klassen):**
- Analysieren Sie nicht das ganze Projekt auf einmal
- Fokussieren Sie auf einzelne Module
- Nutzen Sie IntelliJs Scope-Selection

**Memory-Probleme:**
- Schließen Sie andere Programme
- Erhöhen Sie IntelliJ's Heap Size:
  ```
  Help → Edit Custom VM Options
  -Xmx4096m  # 4 GB statt 2 GB
  ```

---

## Troubleshooting

### Plugin lädt nicht

**Problem:** Plugin erscheint nicht im Tools-Menü

**Lösungen:**
1. Prüfen Sie Plugin-Status:
   - `File` → `Settings` → `Plugins`
   - Suchen Sie "IntelliJDeodorant"
   - Status muss sein: ✅ Enabled

2. IntelliJ neu starten:
   - `File` → `Invalidate Caches / Restart`
   - Wählen Sie "Invalidate and Restart"

3. Plugin neu installieren:
   - Deinstallieren Sie Plugin
   - IntelliJ neu starten
   - Plugin erneut installieren

**Problem:** Fehlermeldung beim Laden

**Lösung:**
- Prüfen Sie IntelliJ-Version: Muss ≥ 2021.3 sein
- Prüfen Sie Java-Version: Muss ≥ 11 sein
- Prüfen Sie Logs: `Help` → `Show Log in Explorer`

---

### Analyse schlägt fehl

**Problem:** "Analysis failed" oder "No candidates found"

**Mögliche Ursachen:**

1. **Projekt nicht kompiliert:**
   ```
   Lösung: Build → Rebuild Project
   ```

2. **Keine Source-Files im Scope:**
   ```
   Lösung: Prüfen Sie, dass Sie Java-Dateien haben
   ```

3. **Memory-Problem:**
   ```
   Fehlermeldung: OutOfMemoryError
   Lösung: Erhöhen Sie Heap Size (siehe oben)
   ```

4. **Threading-Konflikt:**
   ```
   Fehlermeldung: "Read access is allowed..."
   Lösung: Schließen Sie andere Analyse-Tools
           Warten Sie, bis Indexierung fertig ist
   ```

---

### Refactoring funktioniert nicht

**Problem:** "Refactoring cannot be performed"

**Lösungen:**

1. **Code ist nicht kompilierbar:**
   - Stellen Sie sicher, dass Code keine Fehler hat
   - `Build` → `Build Project` sollte erfolgreich sein

2. **Datei ist Read-Only:**
   - Prüfen Sie Dateiberechtigungen
   - Bei VCS: Checken Sie Datei aus

3. **Code ist bereits optimiert:**
   - Plugin findet keine Verbesserungsmöglichkeiten
   - Das ist gut!

**Problem:** Refactoring bricht Code

**Sofortmaßnahmen:**
1. `Ctrl+Z` (Undo)
2. Oder: `VCS` → `Rollback Changes`

**Prävention:**
- Immer Tests vor Refactoring durchführen
- Branch erstellen vor großen Refactorings
- Code-Review nach Refactoring

---

### Performance-Probleme

**Problem:** Analyse dauert sehr lange (>5 Minuten)

**Lösungen:**

1. **Scope reduzieren:**
   ```
   Statt ganzes Projekt:
   - Analysieren Sie nur src/main/
   - Oder nur einzelne Packages
   ```

2. **Parallel-Analysen vermeiden:**
   - Nur eine Detection-Art gleichzeitig
   - Keine anderen Scans parallel (z.B. IntelliJ's Inspections)

3. **Cache leeren:**
   ```
   File → Invalidate Caches / Restart
   ```

**Problem:** UI friert ein

**Lösung:**
- Das sollte nicht passieren (Background-Tasks!)
- Falls doch: `Help` → `Create Issue` mit Logs

---

## FAQ

### Allgemeine Fragen

**F: Ist das Plugin kostenlos?**
A: Ja, IntelliJDeodorant ist Open Source und kostenlos.

**F: Funktioniert es mit Community Edition?**
A: Ja, sowohl Community als auch Ultimate Edition werden unterstützt.

**F: Welche IntelliJ-Versionen werden unterstützt?**
A: IntelliJ IDEA 2021.3 bis 2025.2 (und zukünftige Versionen).

**F: Kann ich es für kommerzielle Projekte nutzen?**
A: Ja, das Plugin hat eine MIT-Lizenz.

**F: Werden andere Sprachen außer Java unterstützt?**
A: Aktuell nur Java. Kotlin-Support ist für die Zukunft geplant.

---

### Technische Fragen

**F: Wie genau sind die Erkennungen?**
A: Die Erkennungen basieren auf wissenschaftlichen Metriken mit hoher Präzision:
- Feature Envy: ~85% Präzision
- God Class: ~90% Präzision
- Long Method: ~95% Präzision
- Type Checking: ~90% Präzision

(Basierend auf Evaluationen in akademischen Papers)

**F: Kann das Plugin falsche Vorschläge machen?**
A: Ja, wie alle automatischen Tools sind False Positives möglich. Daher:
- ✅ Immer Preview anschauen
- ✅ Tests vor und nach Refactoring
- ✅ Code-Review

**F: Werden Refactorings automatisch angewendet?**
A: Nein! Alle Refactorings erfordern Ihre Bestätigung:
1. Sie wählen Kandidat aus
2. Sie sehen Preview
3. Sie klicken "Apply"

**F: Was passiert bei fehlgeschlagenem Refactoring?**
A: IntelliJ's Refactoring-Engine ist sicher:
- Automatisches Rollback bei Fehlern
- Undo (Ctrl+Z) funktioniert immer
- VCS-Integration für zusätzliche Sicherheit

---

### Workflow-Fragen

**F: Sollte ich alle gefundenen Smells beheben?**
A: Nein! Priorisieren Sie:
- Häufig geänderte Klassen zuerst
- Klassen mit vielen Bugs
- Klassen, die schwer zu testen sind

**F: Wie oft sollte ich Analysen durchführen?**
A: Empfehlung:
- **Täglich:** Nach größeren Features
- **Wöchentlich:** Im normalen Entwicklungszyklus
- **Monatlich:** Projekt-weite Refactoring-Sprints

**F: Kann ich Analysen in CI/CD integrieren?**
A: Noch nicht direkt. Aber:
- IntelliJ's Inspection-Reports können exportiert werden
- Zukünftiges Feature: Command-Line-Interface geplant

---

## Weiterführende Ressourcen

### Dokumentation

- **Entwickler-Guide:** [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
- **Projekt-Dokumentation:** [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md)

### Wissenschaftliche Papers

- Tsantalis et al. (2011): "Identification of Move Method Refactoring Opportunities"
- Fokaefs et al. (2012): "Identification of Extract Class Refactoring Opportunities"
- Fowler, Martin (2018): "Refactoring: Improving the Design of Existing Code"

### Online-Ressourcen

- **Refactoring Guru:** https://refactoring.guru/refactoring/smells
- **Martin Fowler's Catalog:** https://refactoring.com/catalog/
- **IntelliJ Refactoring Guide:** https://www.jetbrains.com/help/idea/refactoring-source-code.html

---

## Support

### Bei Problemen

1. **Prüfen Sie diese FAQ** (siehe oben)
2. **Durchsuchen Sie GitLab Issues:** [Issues](https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis/-/issues)
3. **Erstellen Sie ein neues Issue:**
   - Titel: `[BUG] Kurze Beschreibung`
   - Fügen Sie hinzu:
     - IntelliJ-Version
     - Java-Version
     - Plugin-Version
     - Schritte zur Reproduktion
     - Logs (siehe unten)

### Logs finden

```
Windows: C:\Users\[Username]\AppData\Local\JetBrains\[IdeaVersion]\log\idea.log
macOS: ~/Library/Logs/JetBrains/[IdeaVersion]/idea.log
Linux: ~/.cache/JetBrains/[IdeaVersion]/log/idea.log
```

Oder in IntelliJ:
```
Help → Show Log in Explorer
```

### Feedback geben

Wir freuen uns über Feedback!
- **Bug-Reports:** [GitLab Issues](https://gitlab.uni-marburg.de/datengta/intellijdeodorant-thesis/-/issues)
- **Feature-Requests:** [GitLab Issues](https://gitlab.com/[your-repo]/intellijdeodorant/-/issues) mit Label "enhancement"
- **Star auf GitLab:** Wenn Ihnen das Plugin hilft!

---

**Benutzerhandbuch Version:** 1.0  
**Letzte Aktualisierung:** 21. Januar 2026  
**Autor:** Dateng Tankoua Emery Josian  
**Kontakt:** datengtankoua@gmail

---

**Viel Erfolg beim Refactoring!**
