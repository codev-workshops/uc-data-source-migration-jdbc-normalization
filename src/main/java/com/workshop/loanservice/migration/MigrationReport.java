package com.workshop.loanservice.migration;

import java.util.List;
import java.util.stream.Collectors;

/** Per-table results of a legacy → modern migration run. */
public record MigrationReport(List<TableMigrationResult> tables) {

  public TableMigrationResult table(String name) {
    return tables.stream()
        .filter(result -> result.getTable().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown table: " + name));
  }

  public String summary() {
    return tables.stream()
        .map(TableMigrationResult::toString)
        .collect(Collectors.joining("\n  ", "Migration summary:\n  ", ""));
  }
}
