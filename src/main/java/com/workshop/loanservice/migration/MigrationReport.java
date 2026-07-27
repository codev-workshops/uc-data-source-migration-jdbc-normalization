package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Row counts, skipped records, aggregate amount checks and PASS/FAIL criteria collected while
 * migrating. Rendered by {@link DataMigrationRunner}, which exits non-zero when {@link #passed()}
 * is false.
 */
public class MigrationReport {

    /** One skipped legacy record and why it was skipped. */
    public record Skip(String legacyId, String reason) {
    }

    /** A legacy code the mapping document does not expand; migrated through as-is. */
    public record MappingGap(String legacyId, String field, String code) {
    }

    /** One PASS/FAIL line of the validation section. */
    public record Criterion(String description, boolean passed, String detail) {
    }

    /** Per-table counts and aggregate amount sums. */
    public static class TableReport {

        private final String legacyTable;
        private final String modernTable;
        private long legacyCount;
        private int migrated;
        private int alreadyMigrated;
        private long mapped;
        private final List<Skip> skipped = new ArrayList<>();
        private final List<MappingGap> mappingGaps = new ArrayList<>();
        private final Map<String, BigDecimal> legacySums = new LinkedHashMap<>();
        private final Map<String, BigDecimal> modernSums = new LinkedHashMap<>();

        TableReport(String legacyTable, String modernTable) {
            this.legacyTable = legacyTable;
            this.modernTable = modernTable;
        }

        public String getModernTable() { return modernTable; }
        public long getLegacyCount() { return legacyCount; }
        void setLegacyCount(long legacyCount) { this.legacyCount = legacyCount; }
        public int getMigrated() { return migrated; }
        void incrementMigrated() { this.migrated++; }
        public int getAlreadyMigrated() { return alreadyMigrated; }
        void incrementAlreadyMigrated() { this.alreadyMigrated++; }
        public long getMapped() { return mapped; }
        void setMapped(long mapped) { this.mapped = mapped; }
        public List<Skip> getSkipped() { return skipped; }
        void addSkip(String legacyId, String reason) { skipped.add(new Skip(legacyId, reason)); }
        public List<MappingGap> getMappingGaps() { return mappingGaps; }
        void addMappingGap(String legacyId, String field, String code) {
            mappingGaps.add(new MappingGap(legacyId, field, code));
        }
        void addLegacySum(String column, BigDecimal value) { legacySums.put(column, value); }
        void addModernSum(String column, BigDecimal value) { modernSums.put(column, value); }
        Map<String, BigDecimal> getLegacySums() { return legacySums; }
        Map<String, BigDecimal> getModernSums() { return modernSums; }
    }

    private final List<TableReport> tables = new ArrayList<>();
    private final List<Criterion> criteria = new ArrayList<>();

    TableReport table(String legacyTable, String modernTable) {
        TableReport table = new TableReport(legacyTable, modernTable);
        tables.add(table);
        return table;
    }

    void addCriterion(String description, boolean passed, String detail) {
        criteria.add(new Criterion(description, passed, detail));
    }

    public List<TableReport> getTables() {
        return tables;
    }

    public List<Criterion> getCriteria() {
        return criteria;
    }

    public boolean passed() {
        return criteria.stream().allMatch(Criterion::passed) && amountChecksPassed();
    }

    private boolean amountChecksPassed() {
        return tables.stream().allMatch(table -> table.legacySums.entrySet().stream()
                .allMatch(entry -> {
                    BigDecimal modern = table.modernSums.get(entry.getKey());
                    return modern != null && entry.getValue().compareTo(modern) == 0;
                }));
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("\n========================= MIGRATION REPORT =========================\n");

        out.append("\n-- Row counts ------------------------------------------------------\n");
        for (TableReport table : tables) {
            out.append(String.format("%-14s -> %-14s legacy=%d migrated=%d already-migrated=%d "
                            + "malformed-skipped=%d total-mapped=%d%n",
                    table.legacyTable, table.modernTable, table.legacyCount, table.migrated,
                    table.alreadyMigrated, table.skipped.size(), table.mapped));
        }

        out.append("\n-- Skipped records -------------------------------------------------\n");
        boolean anySkips = false;
        for (TableReport table : tables) {
            for (Skip skip : table.skipped) {
                anySkips = true;
                out.append(String.format("%-14s %-16s %s%n", table.modernTable, skip.legacyId(), skip.reason()));
            }
        }
        if (!anySkips) {
            out.append("(none)\n");
        }

        out.append("\n-- Codes migrated unexpanded (column_mappings.md gaps) -----------\n");
        boolean anyGaps = false;
        for (TableReport table : tables) {
            for (MappingGap gap : table.mappingGaps) {
                anyGaps = true;
                out.append(String.format("%-14s %-16s %-13s '%s' kept as-is%n", table.modernTable,
                        gap.legacyId(), gap.field(), gap.code()));
            }
        }
        if (!anyGaps) {
            out.append("(none)\n");
        }

        out.append("\n-- Aggregate amount checks (migrated rows only) --------------------\n");
        for (TableReport table : tables) {
            for (Map.Entry<String, BigDecimal> entry : table.legacySums.entrySet()) {
                BigDecimal legacy = entry.getValue();
                BigDecimal modern = table.modernSums.get(entry.getKey());
                boolean equal = modern != null && legacy.compareTo(modern) == 0;
                out.append(String.format("[%s] %s.%s legacy=%s modern=%s%n", equal ? "PASS" : "FAIL",
                        table.modernTable, entry.getKey(), legacy.toPlainString(),
                        modern == null ? "n/a" : modern.toPlainString()));
            }
        }

        out.append("\n-- Validation criteria ---------------------------------------------\n");
        for (Criterion criterion : criteria) {
            out.append(String.format("[%s] %s%s%n", criterion.passed() ? "PASS" : "FAIL",
                    criterion.description(),
                    criterion.detail() == null || criterion.detail().isEmpty()
                            ? "" : " -- " + criterion.detail()));
        }

        out.append(String.format("%nOVERALL: %s%n", passed() ? "PASS" : "FAIL"));
        out.append("====================================================================\n");
        return out.toString();
    }
}
