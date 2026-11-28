package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-table outcome of a legacy-to-modern migration run.
 *
 * <p>Every legacy row read ends up in exactly one bucket: inserted, skipped because it
 * already exists in the modern schema, rejected (bad data or unresolved parent), or
 * duplicate (its natural key already appeared earlier in the same legacy table).
 */
public class MigrationReport {

    private final Map<String, TableReport> tables = new LinkedHashMap<>();

    TableReport table(String name) {
        return tables.computeIfAbsent(name, TableReport::new);
    }

    public TableReport get(String table) {
        return tables.get(table);
    }

    public Map<String, TableReport> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    public boolean hasRejections() {
        return tables.values().stream().anyMatch(t -> t.getExcludedCount() > 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        tables.values().forEach(t -> sb.append(System.lineSeparator()).append("  ").append(t));
        return sb.toString();
    }

    public record Rejection(String legacyKey, String reason) {
    }

    public static class TableReport {

        private final String table;
        private int read;
        private int inserted;
        private int skippedExisting;
        private final List<Rejection> rejections = new ArrayList<>();
        private final List<Rejection> duplicates = new ArrayList<>();
        private final Set<String> rejectedKeys = new HashSet<>();

        TableReport(String table) {
            this.table = table;
        }

        void recordRead() { read++; }
        void recordInserted() { inserted++; }
        void recordSkippedExisting() { skippedExisting++; }

        void recordRejected(String legacyKey, String reason) {
            rejections.add(new Rejection(legacyKey, reason));
            if (legacyKey != null) rejectedKeys.add(legacyKey);
        }

        void recordDuplicate(String legacyKey, String reason) {
            duplicates.add(new Rejection(legacyKey, reason));
        }

        /** Whether the first legacy row with this natural key was rejected. */
        public boolean isRejected(String legacyKey) { return rejectedKeys.contains(legacyKey); }

        public String getTable() { return table; }
        public int getRead() { return read; }
        public int getInserted() { return inserted; }
        public int getSkippedExisting() { return skippedExisting; }
        public List<Rejection> getRejections() { return Collections.unmodifiableList(rejections); }
        public List<Rejection> getDuplicates() { return Collections.unmodifiableList(duplicates); }

        /** Legacy rows that intentionally have no modern counterpart. */
        public int getExcludedCount() { return rejections.size() + duplicates.size(); }

        @Override
        public String toString() {
            return String.format("%-14s read=%d inserted=%d skippedExisting=%d rejected=%d duplicates=%d%s%s",
                    table, read, inserted, skippedExisting, rejections.size(), duplicates.size(),
                    rejections.isEmpty() ? "" : " rejected=" + rejections,
                    duplicates.isEmpty() ? "" : " duplicates=" + duplicates);
        }
    }
}
