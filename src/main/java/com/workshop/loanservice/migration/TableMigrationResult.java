package com.workshop.loanservice.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of migrating one legacy table. */
public class TableMigrationResult {

  private final String table;
  private final int sourceRows;
  private int migrated;
  private int skippedDuplicates;
  private final Map<String, String> rejections = new LinkedHashMap<>();

  public TableMigrationResult(String table, int sourceRows) {
    this.table = table;
    this.sourceRows = sourceRows;
  }

  void recordMigrated() {
    migrated++;
  }

  void recordSkippedDuplicate() {
    skippedDuplicates++;
  }

  void recordRejected(String legacyKey, String reason) {
    rejections.put(String.valueOf(legacyKey), reason);
  }

  public String getTable() {
    return table;
  }

  public int getSourceRows() {
    return sourceRows;
  }

  public int getMigrated() {
    return migrated;
  }

  public int getSkippedDuplicates() {
    return skippedDuplicates;
  }

  public Map<String, String> getRejections() {
    return Collections.unmodifiableMap(rejections);
  }

  @Override
  public String toString() {
    return String.format("%-14s source=%d migrated=%d skippedDuplicates=%d rejected=%d%s",
        table, sourceRows, migrated, skippedDuplicates, rejections.size(),
        rejections.isEmpty() ? "" : " " + rejections);
  }
}
