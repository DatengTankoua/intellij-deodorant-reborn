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
     * @param matchCount Anzahl der gefundenen Matches durch PMD
     * @param adjustedGroupCount Anzahl der Gruppen nach Anpassung
     */
    public static void validate(@NotNull Set<DuplicateCodeGroup> groups, int matchCount, int adjustedGroupCount) {
        int initialGroupCount = groups.size();
        int removedByFeasibility = ExtractMethodFeasibilityChecker.validate(groups);
        int groupsAfterFeasibility = groups.size();
        int newGroupsBySimilarity  = DuplicateSimilarityChecker.validate(groups);
        int removedGroupsBySimilarity = groupsAfterFeasibility - groups.size() + newGroupsBySimilarity;

         LOG.info("=== SUMMARY ===");
        LOG.info("Total matches found by PMD: " + matchCount);
        LOG.info("Total groups created after adjustment: " + adjustedGroupCount);
        LOG.info("Total groups after merging: " + initialGroupCount);
        LOG.info("Validation completed - new groups by similarity: " + newGroupsBySimilarity
               + ", removed by feasibility: " + removedByFeasibility
               + ", removed by similarity: " + removedGroupsBySimilarity);
    }
}
