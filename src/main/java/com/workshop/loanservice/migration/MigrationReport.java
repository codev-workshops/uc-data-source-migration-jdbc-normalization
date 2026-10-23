package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of a legacy-to-modern migration run: per-table counters, issues and reconciliation checks. */
public class MigrationReport {

    private final Map<String, TableStats> tables = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<ReconciliationCheck> checks = new ArrayList<>();

    TableStats table(String name) {
        return tables.computeIfAbsent(name, TableStats::new);
    }

    void error(String message) {
        errors.add(message);
    }

    void warning(String message) {
        warnings.add(message);
    }

    void check(String name, Object legacyValue, Object modernValue) {
        checks.add(new ReconciliationCheck(name, legacyValue, modernValue));
    }

    public Map<String, TableStats> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    public TableStats getTable(String name) {
        return tables.get(name);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<ReconciliationCheck> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    /** True when no row was rejected and every reconciliation check matched. */
    public boolean isSuccessful() {
        return errors.isEmpty() && checks.stream().allMatch(ReconciliationCheck::matches);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder("Legacy -> modern migration ")
                .append(isSuccessful() ? "SUCCEEDED" : "FAILED");
        tables.values().forEach(t -> sb.append(System.lineSeparator()).append("  ").append(t));
        checks.forEach(c -> sb.append(System.lineSeparator()).append("  ").append(c));
        warnings.forEach(w -> sb.append(System.lineSeparator()).append("  WARN  ").append(w));
        errors.forEach(e -> sb.append(System.lineSeparator()).append("  ERROR ").append(e));
        return sb.toString();
    }

    /** Row counters for one target table. */
    public static class TableStats {
        private final String name;
        private int read;
        private int inserted;
        private int skippedDuplicates;
        private int rejected;

        TableStats(String name) {
            this.name = name;
        }

        void recordRead() { read++; }
        void recordInserted() { inserted++; }
        void recordDuplicate() { skippedDuplicates++; }
        void recordRejected() { rejected++; }

        public String getName() { return name; }
        public int getRead() { return read; }
        public int getInserted() { return inserted; }
        public int getSkippedDuplicates() { return skippedDuplicates; }
        public int getRejected() { return rejected; }

        @Override
        public String toString() {
            return String.format("%-14s read=%d inserted=%d duplicates=%d rejected=%d",
                    name, read, inserted, skippedDuplicates, rejected);
        }
    }

    /** Compares a legacy aggregate with the same aggregate over the modern table. */
    public record ReconciliationCheck(String name, Object legacyValue, Object modernValue) {

        public boolean matches() {
            if (legacyValue instanceof BigDecimal legacy && modernValue instanceof BigDecimal modern) {
                return legacy.compareTo(modern) == 0;
            }
            return legacyValue != null && legacyValue.equals(modernValue);
        }

        @Override
        public String toString() {
            return String.format("%-5s %-28s legacy=%s modern=%s",
                    matches() ? "OK" : "DIFF", name, legacyValue, modernValue);
        }
    }
}
