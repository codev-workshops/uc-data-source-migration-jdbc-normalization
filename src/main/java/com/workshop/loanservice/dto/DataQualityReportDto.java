package com.workshop.loanservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated data-quality report for the legacy CDW tables.
 *
 * @param generatedAt ISO-8601 timestamp of the run
 * @param summary global counts and average score
 * @param tables per-table statistics, keyed by legacy table name
 * @param rules per-rule statistics, keyed by rule id
 * @param records per-record scores
 * @param violations every violation found, bound to its table and record
 */
public record DataQualityReportDto(
    String generatedAt,
    Summary summary,
    Map<String, TableStats> tables,
    Map<String, RuleStats> rules,
    List<RecordScore> records,
    List<ViolationDto> violations) {

  /** Whole-dataset totals. */
  public record Summary(
      int recordCount,
      int errorCount,
      int warnCount,
      int recordsWithErrors,
      int recordsWithWarnings,
      double averageScore) {}

  /** Totals for a single legacy table. */
  public record TableStats(
      int recordCount,
      int errorCount,
      int warnCount,
      double averageScore,
      Map<String, Integer> violationsByRule) {}

  /** Totals for a single rule. */
  public record RuleStats(String severity, int count, List<String> affectedRecords) {}

  /** Score for a single record: 100 minus 25 per ERROR and 5 per WARN, floored at 0. */
  public record RecordScore(
      String entityType, String recordKey, int score, int errorCount, int warnCount) {}

  /** A bound violation. */
  public record ViolationDto(
      String entityType,
      String recordKey,
      String field,
      String rawValue,
      String ruleId,
      String severity,
      String message) {}
}
