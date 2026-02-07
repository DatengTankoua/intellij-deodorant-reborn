package org.jetbrains.research.intellijdeodorant.core.duplication;

import com.intellij.openapi.application.ReadAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Gruppiert mehrere DuplicateCodeFragments, die denselben Code enthalten.
 * 
 * Eine Gruppe repräsentiert einen "Clone-Set" - eine Menge von Code-Fragmenten,
 * die alle identisch oder sehr ähnlich sind.
 * 
 * @author IntelliJDeodorant Team
 * @version 2.0
 */
public class DuplicateCodeGroup {
    
    private final List<DuplicateCodeFragment> fragments;
    private final int tokens;
    private final int occurrences;
    
    public DuplicateCodeGroup(int tokens) {
        this.fragments = new ArrayList<>();
        this.tokens = tokens;
        this.occurrences = 0;
    }
    
    public DuplicateCodeGroup(@NotNull List<DuplicateCodeFragment> fragments, int tokens) {
        this.fragments = new ArrayList<>(fragments);
        this.tokens = tokens;
        this.occurrences = fragments.size();
    }
    
    public void addFragment(@NotNull DuplicateCodeFragment fragment) {
        if (!fragments.contains(fragment)) {
            fragments.add(fragment);
        }
    }
    
    @NotNull
    public List<DuplicateCodeFragment> getFragments() {
        return Collections.unmodifiableList(fragments);
    }
    
    @NotNull
    public DuplicateCodeFragment getFirstFragment() {
        if (fragments.isEmpty()) {
            throw new IllegalStateException("Group has no fragments");
        }
        return fragments.get(0);
    }
    
    public int getOccurrences() {
        return fragments.size();
    }
    
    public int getTokens() {
        return tokens;
    }
    
    public double getAverageLines() {
        if (fragments.isEmpty()) {
            return 0;
        }
        return fragments.stream()
            .mapToInt(DuplicateCodeFragment::getLineCount)
            .average()
            .orElse(0);
    }
    
    public int getSeverity() {
        return tokens * getOccurrences();
    }
    
    public boolean isCrossFile() {
        if (fragments.size() <= 1) {
            return false;
        }
        
        return ReadAction.compute(() -> {
            String firstFile = fragments.get(0).getFilePath();
            return fragments.stream()
                .anyMatch(f -> !f.getFilePath().equals(firstFile));
        });
    }
    
    @NotNull
    public String getSummary() {
        return String.format("%d occurrences, %d tokens, avg %.1f lines",
            getOccurrences(), tokens, getAverageLines());
    }
    
    @NotNull
    public String getCode() {
        return getFirstFragment().getCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DuplicateCodeGroup that = (DuplicateCodeGroup) o;
        return tokens == that.tokens &&
               Objects.equals(fragments, that.fragments);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fragments, tokens);
    }
    
    @Override
    public String toString() {
        return "DuplicateCodeGroup{" +
               "occurrences=" + getOccurrences() +
               ", tokens=" + tokens +
               ", severity=" + getSeverity() +
               ", crossFile=" + isCrossFile() +
               '}';
    }
}
