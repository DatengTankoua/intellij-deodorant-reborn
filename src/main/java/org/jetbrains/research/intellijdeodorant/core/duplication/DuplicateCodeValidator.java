package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Koordiniert alle Validierungsschritte für Duplikat-Gruppen.
 *
 * Delegiert an:
 * - {@link DuplicateSimilarityChecker}: Prüft ob Fragmente strukturell ähnlich genug sind
 * - {@link ExtractMethodFeasibilityChecker}: Prüft ob Extract Method ausführbar ist
 */
public class DuplicateCodeValidator {

    private static final Logger LOG = Logger.getInstance(DuplicateCodeValidator.class);

    /**
     * Führt alle Validierungsschritte durch und entfernt ungültige Fragmente/Gruppen.
     *
     * @param groups Zu validierende Gruppen (werden direkt modifiziert)
     */
    public static void validate(@NotNull Set<DuplicateCodeGroup> groups) {
        int newGroupsBySimilarity  = DuplicateSimilarityChecker.validate(groups);
        int removedByFeasibility = ExtractMethodFeasibilityChecker.validate(groups);
        groups.removeIf(group -> group.getOccurrences() < 2);

        LOG.info("Validation completed — new groups by similarity: " + newGroupsBySimilarity
               + ", removed by feasibility: " + removedByFeasibility);
    }
}
