package com.workshop.loanservice.reconciliation;

import java.util.ArrayList;
import java.util.List;

/** Outcome of comparing the legacy and modern data sources check by check. */
public class ReconciliationReport {

    public static class Check {
        private final String name;
        private final String legacyValue;
        private final String modernValue;
        private final List<String> differences;

        Check(String name, String legacyValue, String modernValue, List<String> differences) {
            this.name = name;
            this.legacyValue = legacyValue;
            this.modernValue = modernValue;
            this.differences = differences;
        }

        public String getName() { return name; }
        public String getLegacyValue() { return legacyValue; }
        public String getModernValue() { return modernValue; }
        public List<String> getDifferences() { return differences; }
        public boolean isMatched() { return differences.isEmpty(); }
    }

    private final List<Check> checks = new ArrayList<>();

    void add(String name, Object legacyValue, Object modernValue) {
        String legacy = String.valueOf(legacyValue);
        String modern = String.valueOf(modernValue);
        add(name, legacy, modern, legacy.equals(modern) ? List.of() : List.of(name + " differs"));
    }

    void add(String name, String legacyValue, String modernValue, List<String> differences) {
        checks.add(new Check(name, legacyValue, modernValue, differences));
    }

    public List<Check> getChecks() {
        return checks;
    }

    public boolean isMatched() {
        return checks.stream().allMatch(Check::isMatched);
    }

    public List<String> getMismatches() {
        return checks.stream()
                .filter(c -> !c.isMatched())
                .flatMap(c -> c.getDifferences().stream())
                .toList();
    }
}
