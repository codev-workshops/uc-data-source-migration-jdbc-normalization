package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a migration run: per-table inserted/updated/skipped/failed counts
 * plus contextual failure records (table, business key, field, invalid value).
 */
public class MigrationReport {

    /** A single contextual failure encountered while migrating one record. */
    public static final class Failure {
        private final String table;
        private final String businessKey;
        private final String field;
        private final String invalidValue;
        private final String message;

        public Failure(String table, String businessKey, String field, String invalidValue, String message) {
            this.table = table;
            this.businessKey = businessKey;
            this.field = field;
            this.invalidValue = invalidValue;
            this.message = message;
        }

        public String getTable() { return table; }
        public String getBusinessKey() { return businessKey; }
        public String getField() { return field; }
        public String getInvalidValue() { return invalidValue; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "FAILED table=" + table + " businessKey=" + businessKey
                    + " field=" + field + " invalidValue=" + invalidValue + " :: " + message;
        }
    }

    /** Counts and failures for a single table. */
    public static final class TableResult {
        private int inserted;
        private int updated;
        private int skipped;
        private int failed;
        private final List<Failure> failures = new ArrayList<>();

        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public List<Failure> getFailures() { return failures; }
    }

    private final Map<String, TableResult> tables = new LinkedHashMap<>();

    private TableResult table(String table) {
        return tables.computeIfAbsent(table, t -> new TableResult());
    }

    public void recordInserted(String table) { table(table).inserted++; }
    public void recordUpdated(String table) { table(table).updated++; }
    public void recordSkipped(String table) { table(table).skipped++; }

    public void recordFailure(String table, String businessKey, String field, String invalidValue, String message) {
        TableResult result = table(table);
        result.failed++;
        result.failures.add(new Failure(table, businessKey, field, invalidValue, message));
    }

    public TableResult getTable(String table) {
        return tables.computeIfAbsent(table, t -> new TableResult());
    }

    public Map<String, TableResult> getTables() {
        return tables;
    }

    public int getInserted() { return tables.values().stream().mapToInt(TableResult::getInserted).sum(); }
    public int getUpdated() { return tables.values().stream().mapToInt(TableResult::getUpdated).sum(); }
    public int getSkipped() { return tables.values().stream().mapToInt(TableResult::getSkipped).sum(); }
    public int getFailed() { return tables.values().stream().mapToInt(TableResult::getFailed).sum(); }

    public boolean hasFailures() {
        return getFailed() > 0;
    }

    public List<Failure> getAllFailures() {
        List<Failure> all = new ArrayList<>();
        tables.values().forEach(t -> all.addAll(t.failures));
        return all;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MigrationReport[");
        sb.append("inserted=").append(getInserted())
          .append(", updated=").append(getUpdated())
          .append(", skipped=").append(getSkipped())
          .append(", failed=").append(getFailed()).append("]");
        tables.forEach((name, r) -> sb.append("\n  ").append(name)
                .append(": inserted=").append(r.inserted)
                .append(", updated=").append(r.updated)
                .append(", skipped=").append(r.skipped)
                .append(", failed=").append(r.failed));
        getAllFailures().forEach(f -> sb.append("\n  ").append(f));
        return sb.toString();
    }
}
