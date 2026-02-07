package org.jetbrains.research.intellijdeodorant.core.distance;

/**
 * Typen von strukturellen Unterschieden zwischen Clone-Kandidaten.
 */
public enum DifferenceType {
    
    /**
     * Unterschiedliche Variablennamen
     */
    VARIABLE_NAME_MISMATCH(true, 2),
    
    /**
     * Unterschiedliche Variablentypen
     */
    VARIABLE_TYPE_MISMATCH(false, 50),
    
    /**
     * Unterschiedliche Literal-Werte
     */
    LITERAL_VALUE_MISMATCH(true, 5),
    
    /**
     * Unterschiedliche Methodennamen
     */
    METHOD_NAME_MISMATCH(true, 10),
    
    /**
     * Unterschiedliche Anzahl von Argumenten
     */
    ARGUMENT_COUNT_MISMATCH(false, 100),
    
    /**
     * Unterschiedliche AST-Struktur
     */
    STRUCTURE_MISMATCH(false, 100),
    
    /**
     * Unterschiedliche Operatoren
     */
    OPERATOR_MISMATCH(false, 30);
    
    private final boolean parameterizable;
    private final int penaltyWeight;
    
    DifferenceType(boolean parameterizable, int penaltyWeight) {
        this.parameterizable = parameterizable;
        this.penaltyWeight = penaltyWeight;
    }
    
    /**
     * @return true wenn dieser Unterschied durch Extract Method parameterisiert werden kann
     */
    public boolean isParameterizable() {
        return parameterizable;
    }
    
    /**
     * @return Straf-Gewicht für Qualitäts-Score
     */
    public int getPenaltyWeight() {
        return penaltyWeight;
    }
}
