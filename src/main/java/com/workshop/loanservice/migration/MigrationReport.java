package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.QuarantineReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of one {@link LegacyToModernMigrator#migrate()} run. */
public final class MigrationReport {

  /** Row counts for one target table. */
  public record TableCounts(int read, int loaded, int quarantined) {}

  /** A legacy row written to {@code migration_quarantine}. */
  public record QuarantinedRow(
      String sourceTable, String sourceKey, QuarantineReason reason, String field, String detail) {}

  /** A loan row whose denormalised borrower name differs from {@code CDW_BORR_MSTR}. */
  public record NameDivergence(
      String loanAccountNumber, String borrowerId, String loanRowName, String masterName) {}

  private final Map<String, int[]> counts = new LinkedHashMap<>();
  private final List<QuarantinedRow> quarantinedRows = new ArrayList<>();
  private final List<NameDivergence> nameDivergences = new ArrayList<>();

  void recordRead(String table) {
    tally(table)[0]++;
  }

  void recordLoaded(String table) {
    tally(table)[1]++;
  }

  void recordQuarantined(String table, QuarantinedRow row) {
    tally(table)[2]++;
    quarantinedRows.add(row);
  }

  void recordNameDivergence(NameDivergence divergence) {
    nameDivergences.add(divergence);
  }

  public TableCounts counts(String table) {
    int[] tally = counts.getOrDefault(table, new int[3]);
    return new TableCounts(tally[0], tally[1], tally[2]);
  }

  public List<QuarantinedRow> quarantinedRows() {
    return Collections.unmodifiableList(quarantinedRows);
  }

  public List<NameDivergence> nameDivergences() {
    return Collections.unmodifiableList(nameDivergences);
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder("Migration report");
    counts.forEach(
        (table, tally) ->
            out.append(
                String.format(
                    "%n  %-14s read=%d loaded=%d quarantined=%d",
                    table, tally[0], tally[1], tally[2])));
    quarantinedRows.forEach(
        row ->
            out.append(
                String.format(
                    "%n  QUARANTINED %s %s: %s on %s - %s",
                    row.sourceTable(), row.sourceKey(), row.reason(), row.field(), row.detail())));
    nameDivergences.forEach(
        d ->
            out.append(
                String.format(
                    "%n  NAME DIVERGENCE %s (%s): loan row '%s' vs master '%s'",
                    d.loanAccountNumber(), d.borrowerId(), d.loanRowName(), d.masterName())));
    return out.toString();
  }

  private int[] tally(String table) {
    return counts.computeIfAbsent(table, t -> new int[3]);
  }
}
