package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.DataQualityReportDto;
import com.workshop.loanservice.dto.DataQualityReportDto.RecordScore;
import com.workshop.loanservice.dto.DataQualityReportDto.RuleStats;
import com.workshop.loanservice.dto.DataQualityReportDto.Summary;
import com.workshop.loanservice.dto.DataQualityReportDto.TableStats;
import com.workshop.loanservice.dto.DataQualityReportDto.ViolationDto;
import com.workshop.loanservice.service.validation.DataQualityResult;
import com.workshop.loanservice.service.validation.DataQualityValidator;
import com.workshop.loanservice.service.validation.ValidationViolation;
import com.workshop.loanservice.service.validation.ValidationViolation.Severity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs the {@link DataQualityValidator}, scores each record and aggregates per-table and per-rule
 * statistics. Also renders the report as Markdown.
 */
@Service
public class DataQualityReportService {

  /** Points deducted per {@code ERROR} violation. */
  public static final int ERROR_WEIGHT = 25;

  /** Points deducted per {@code WARN} violation. */
  public static final int WARN_WEIGHT = 5;

  private final DataQualityValidator validator;

  public DataQualityReportService(DataQualityValidator validator) {
    this.validator = validator;
  }

  /** Validates all legacy tables and builds the report. */
  public DataQualityReportDto generateReport() {
    return buildReport(validator.validateAll());
  }

  /** Builds a report from an existing validation result. */
  public DataQualityReportDto buildReport(DataQualityResult result) {
    Map<String, Map<String, List<ValidationViolation>>> byTableAndKey = new LinkedHashMap<>();
    for (ValidationViolation v : result.violations()) {
      byTableAndKey
          .computeIfAbsent(v.entityType(), t -> new LinkedHashMap<>())
          .computeIfAbsent(v.recordKey(), k -> new ArrayList<>())
          .add(v);
    }

    List<RecordScore> records = new ArrayList<>();
    Map<String, TableStats> tables = new LinkedHashMap<>();
    result
        .recordKeysByTable()
        .forEach(
            (table, keys) -> {
              Map<String, List<ValidationViolation>> perKey =
                  byTableAndKey.getOrDefault(table, Map.of());
              List<RecordScore> tableRecords =
                  keys.stream()
                      .map(k -> score(table, k, perKey.getOrDefault(k, List.of())))
                      .collect(Collectors.toList());
              records.addAll(tableRecords);
              List<ValidationViolation> tableViolations =
                  perKey.values().stream().flatMap(List::stream).collect(Collectors.toList());
              Map<String, Integer> byRule = new TreeMap<>();
              tableViolations.forEach(v -> byRule.merge(v.ruleId(), 1, Integer::sum));
              tables.put(
                  table,
                  new TableStats(
                      keys.size(),
                      count(tableViolations, Severity.ERROR),
                      count(tableViolations, Severity.WARN),
                      average(tableRecords),
                      byRule));
            });

    Map<String, RuleStats> rules = new TreeMap<>();
    result.violations().stream()
        .collect(
            Collectors.groupingBy(ValidationViolation::ruleId, TreeMap::new, Collectors.toList()))
        .forEach(
            (rule, vs) ->
                rules.put(
                    rule,
                    new RuleStats(
                        vs.get(0).severity().name(),
                        vs.size(),
                        vs.stream()
                            .map(v -> v.entityType() + ":" + v.recordKey())
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList()))));

    Summary summary =
        new Summary(
            records.size(),
            count(result.violations(), Severity.ERROR),
            count(result.violations(), Severity.WARN),
            (int) records.stream().filter(r -> r.errorCount() > 0).count(),
            (int) records.stream().filter(r -> r.warnCount() > 0).count(),
            average(records));

    List<ViolationDto> violations =
        result.violations().stream()
            .map(
                v ->
                    new ViolationDto(
                        v.entityType(),
                        v.recordKey(),
                        v.field(),
                        v.rawValue(),
                        v.ruleId(),
                        v.severity().name(),
                        v.message()))
            .collect(Collectors.toList());

    return new DataQualityReportDto(
        Instant.now().toString(), summary, tables, rules, records, violations);
  }

  /** Renders a report as GitHub-flavoured Markdown. */
  public String renderMarkdown(DataQualityReportDto report) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Data Quality Report\n\n");
    sb.append("Generated: ").append(report.generatedAt()).append("\n\n");
    Summary s = report.summary();
    sb.append("## Summary\n\n");
    sb.append("| Metric | Value |\n|---|---|\n");
    sb.append("| Records examined | ").append(s.recordCount()).append(" |\n");
    sb.append("| ERROR violations | ").append(s.errorCount()).append(" |\n");
    sb.append("| WARN violations | ").append(s.warnCount()).append(" |\n");
    sb.append("| Records with errors | ").append(s.recordsWithErrors()).append(" |\n");
    sb.append("| Records with warnings | ").append(s.recordsWithWarnings()).append(" |\n");
    sb.append("| Average record score | ")
        .append(String.format("%.1f", s.averageScore()))
        .append(" |\n\n");

    sb.append("## Per-table findings\n\n");
    sb.append("| Table | Records | Errors | Warnings | Avg score | Violations by rule |\n");
    sb.append("|---|---|---|---|---|---|\n");
    report
        .tables()
        .forEach(
            (table, t) ->
                sb.append("| `")
                    .append(table)
                    .append("` | ")
                    .append(t.recordCount())
                    .append(" | ")
                    .append(t.errorCount())
                    .append(" | ")
                    .append(t.warnCount())
                    .append(" | ")
                    .append(String.format("%.1f", t.averageScore()))
                    .append(" | ")
                    .append(
                        t.violationsByRule().entrySet().stream()
                            .map(e -> e.getKey() + "×" + e.getValue())
                            .collect(Collectors.joining(", ")))
                    .append(" |\n"));

    sb.append("\n## Per-rule findings\n\n");
    sb.append("| Rule | Severity | Count | Affected records |\n|---|---|---|---|\n");
    report
        .rules()
        .forEach(
            (rule, r) ->
                sb.append("| `")
                    .append(rule)
                    .append("` | ")
                    .append(r.severity())
                    .append(" | ")
                    .append(r.count())
                    .append(" | ")
                    .append(String.join(", ", r.affectedRecords()))
                    .append(" |\n"));

    sb.append("\n## Record scores\n\n");
    sb.append("Score = 100 − 25 × ERROR − 5 × WARN (floored at 0).\n\n");
    sb.append("| Table | Record | Score | Errors | Warnings |\n|---|---|---|---|---|\n");
    for (RecordScore r : report.records()) {
      sb.append("| `")
          .append(r.entityType())
          .append("` | ")
          .append(r.recordKey())
          .append(" | ")
          .append(r.score())
          .append(" | ")
          .append(r.errorCount())
          .append(" | ")
          .append(r.warnCount())
          .append(" |\n");
    }

    sb.append("\n## Violations\n\n");
    sb.append("| Table | Record | Field | Raw value | Rule | Severity | Message |\n");
    sb.append("|---|---|---|---|---|---|---|\n");
    for (ViolationDto v : report.violations()) {
      sb.append("| `")
          .append(v.entityType())
          .append("` | ")
          .append(v.recordKey())
          .append(" | `")
          .append(v.field())
          .append("` | ")
          .append(v.rawValue() == null ? "*null*" : "`" + v.rawValue() + "`")
          .append(" | `")
          .append(v.ruleId())
          .append("` | ")
          .append(v.severity())
          .append(" | ")
          .append(v.message().replace("|", "\\|"))
          .append(" |\n");
    }
    return sb.toString();
  }

  private static RecordScore score(String table, String key, List<ValidationViolation> vs) {
    int errors = count(vs, Severity.ERROR);
    int warns = count(vs, Severity.WARN);
    int score = Math.max(0, 100 - ERROR_WEIGHT * errors - WARN_WEIGHT * warns);
    return new RecordScore(table, key, score, errors, warns);
  }

  private static int count(List<ValidationViolation> vs, Severity severity) {
    return (int) vs.stream().filter(v -> v.severity() == severity).count();
  }

  private static double average(List<RecordScore> records) {
    return records.isEmpty()
        ? 100.0
        : records.stream().mapToInt(RecordScore::score).average().orElse(100.0);
  }
}
