package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

/**
 * Simple logging utility for duplicate code refactoring operations.
 */
public class RefactoringLogger {
    
    private static final boolean DEBUG_ENABLED = Boolean.getBoolean("intellijdeodorant.debug");
    
    public static void debug(String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[DEBUG] " + message);
        }
    }
    
    public static void info(String message) {
        System.out.println("[INFO] " + message);
    }
    
    public static void error(String message) {
        System.err.println("[ERROR] " + message);
    }
}
