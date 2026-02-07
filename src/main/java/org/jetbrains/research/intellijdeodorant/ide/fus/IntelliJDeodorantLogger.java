package org.jetbrains.research.intellijdeodorant.ide.fus;

/**
 * KOMPATIBILITÄT FIX für IntelliJ 2025.2+
 * 
 * Die originalen FUS (Feature Usage Statistics) APIs wurden geändert/entfernt in IntelliJ 2025.2.
 * Diese Klasse wurde in eine No-Op Implementation umgewandelt, die keine Statistiken sammelt,
 * aber die Kompatibilität mit dem existierenden Code bewahrt.
 * 
 * HINWEIS: Statistiken sind optional und nicht kritisch für die Plugin-Funktionalität.
 * Das Plugin funktioniert vollständig ohne sie.
 */
public class IntelliJDeodorantLogger {
    // Version als konstante Zahl statt von loggerProvider abzurufen
    static public final Integer version = 1;

    /**
     * No-Op Implementation - macht nichts, wirft keine Exceptions
     * 
     * @param group Event log group (wird ignoriert)
     * @param action Action name (wird ignoriert)
     */
    static public void log(Object group, String action) {
        // No-op: Statistiken sind deaktiviert für Kompatibilität
    }

    /**
     * No-Op Implementation - macht nichts, wirft keine Exceptions
     * 
     * @param group Event log group (wird ignoriert)
     * @param action Action name (wird ignoriert)
     * @param data Feature usage data (wird ignoriert)
     */
    static public void log(Object group, String action, Object data) {
        // No-op: Statistiken sind deaktiviert für Kompatibilität
    }
}
