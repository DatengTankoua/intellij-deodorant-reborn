package org.jetbrains.research.intellijdeodorant.ide.fus;

/**
 * KOMPATIBILITÄT FIX für IntelliJ 2025.2+
 * 
 * Die originale StatisticsEventLoggerProvider Klasse existiert nicht mehr oder wurde geändert.
 * Diese Dummy-Klasse wird für die plugin.xml Extension beibehalten, macht aber nichts.
 * 
 * WICHTIG: Diese Klasse wird nicht mehr in plugin.xml verwendet.
 * Wir haben die entsprechende Extension aus plugin.xml entfernt.
 */
public class IntelliJDeodorantLoggerProvider {
    /**
     * No-Op Konstruktor
     */
    public IntelliJDeodorantLoggerProvider() {
        // No-op: Statistiken sind deaktiviert
    }
}
