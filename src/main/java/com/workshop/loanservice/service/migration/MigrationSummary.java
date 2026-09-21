package com.workshop.loanservice.service.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of one migration run: per-table inserted/skipped counts plus quarantined records.
 */
public class MigrationSummary {

    public record TableCounts(int legacy, int inserted, int skipped) {
    }

    public record Quarantined(String table, String recordId, String reason) {
    }

    private final Map<String, TableCounts> tables = new LinkedHashMap<>();
    private final List<Quarantined> quarantined = new ArrayList<>();

    public void addTable(String table, int legacy, int inserted, int skipped) {
        tables.put(table, new TableCounts(legacy, inserted, skipped));
    }

    public void quarantine(String table, String recordId, String reason) {
        quarantined.add(new Quarantined(table, recordId, reason));
    }

    public Map<String, TableCounts> getTables() {
        return tables;
    }

    public List<Quarantined> getQuarantined() {
        return quarantined;
    }

    public int getQuarantinedCount() {
        return quarantined.size();
    }
}
