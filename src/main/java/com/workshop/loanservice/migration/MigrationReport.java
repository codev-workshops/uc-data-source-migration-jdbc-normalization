package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-table outcome of a legacy-to-modern migration run.
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
        return tables.values().stream().anyMatch(t -> !t.getRejections().isEmpty());
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

        TableReport(String table) {
            this.table = table;
        }

        void recordRead() { read++; }
        void recordInserted() { inserted++; }
        void recordSkippedExisting() { skippedExisting++; }
        void recordRejected(String legacyKey, String reason) { rejections.add(new Rejection(legacyKey, reason)); }

        public String getTable() { return table; }
        public int getRead() { return read; }
        public int getInserted() { return inserted; }
        public int getSkippedExisting() { return skippedExisting; }
        public List<Rejection> getRejections() { return Collections.unmodifiableList(rejections); }

        @Override
        public String toString() {
            return String.format("%-14s read=%d inserted=%d skippedExisting=%d rejected=%d %s",
                    table, read, inserted, skippedExisting, rejections.size(),
                    rejections.isEmpty() ? "" : rejections);
        }
    }
}
