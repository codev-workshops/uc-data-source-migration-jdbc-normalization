package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-table outcome of a migration run: how many rows were inserted, how many
 * were already present (reruns are idempotent), and why any row was rejected.
 */
public class MigrationReport {

    public static class TableResult {
        private final String table;
        private int migrated;
        private int skippedExisting;
        private final List<String> rejected = new ArrayList<>();

        TableResult(String table) {
            this.table = table;
        }

        void migrated() { migrated++; }

        void skipped() { skippedExisting++; }

        void reject(String key, String reason) { rejected.add(key + ": " + reason); }

        public String getTable() { return table; }
        public int getMigrated() { return migrated; }
        public int getSkippedExisting() { return skippedExisting; }
        public List<String> getRejected() { return rejected; }
        public int getRejectedCount() { return rejected.size(); }

        @Override
        public String toString() {
            return table + " migrated=" + migrated + " skipped=" + skippedExisting
                    + " rejected=" + rejected.size()
                    + (rejected.isEmpty() ? "" : " " + rejected);
        }
    }

    private final Map<String, TableResult> tables = new LinkedHashMap<>();

    TableResult table(String name) {
        return tables.computeIfAbsent(name, TableResult::new);
    }

    public Map<String, TableResult> getTables() {
        return tables;
    }

    public boolean isClean() {
        return tables.values().stream().allMatch(t -> t.getRejected().isEmpty());
    }

    @Override
    public String toString() {
        return String.join(" | ", tables.values().stream().map(TableResult::toString).toList());
    }
}
