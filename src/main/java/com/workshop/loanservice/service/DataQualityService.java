package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.DataQualityReport;
import com.workshop.loanservice.dto.RecordQualityScore;
import com.workshop.loanservice.dto.TableQualitySummary;
import com.workshop.loanservice.validation.LegacyDataValidator;
import com.workshop.loanservice.validation.Severity;
import com.workshop.loanservice.validation.ValidationFinding;
import com.workshop.loanservice.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scores legacy records from validator findings and aggregates them into a report.
 * Each record starts at {@value #MAX_SCORE}; every finding deducts its severity weight,
 * floored at 0.
 */
@Service
public class DataQualityService {

    static final int MAX_SCORE = 100;
    static final Map<Severity, Integer> DEDUCTIONS = Map.of(
            Severity.ERROR, 25,
            Severity.WARNING, 10);

    private final LegacyDataValidator validator;

    public DataQualityService(LegacyDataValidator validator) {
        this.validator = validator;
    }

    public DataQualityReport generateReport() {
        return buildReport(validator.validate());
    }

    static DataQualityReport buildReport(ValidationResult result) {
        Map<String, List<ValidationFinding>> findingsByRecord = result.findings().stream()
                .collect(Collectors.groupingBy(f -> key(f.table(), f.recordId())));

        List<RecordQualityScore> records = result.recordIdsByTable().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(id -> score(entry.getKey(), id,
                        findingsByRecord.getOrDefault(key(entry.getKey(), id), List.of()))))
                .toList();

        List<TableQualitySummary> tables = result.recordIdsByTable().keySet().stream()
                .map(table -> summarize(table,
                        records.stream().filter(r -> r.table().equals(table)).toList()))
                .toList();

        Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity, countSeverity(result.findings(), severity));
        }

        return new DataQualityReport(records.size(), bySeverity, average(records),
                minimum(records), tables, records, result.findings());
    }

    static RecordQualityScore score(String table, String recordId,
                                    List<ValidationFinding> findings) {
        int deductions = findings.stream().mapToInt(f -> DEDUCTIONS.get(f.severity())).sum();
        return new RecordQualityScore(table, recordId, Math.max(0, MAX_SCORE - deductions),
                countSeverity(findings, Severity.ERROR), countSeverity(findings, Severity.WARNING));
    }

    private static TableQualitySummary summarize(String table, List<RecordQualityScore> records) {
        return new TableQualitySummary(table, records.size(),
                records.stream().mapToLong(RecordQualityScore::errorCount).sum(),
                records.stream().mapToLong(RecordQualityScore::warningCount).sum(),
                average(records), minimum(records));
    }

    private static long countSeverity(List<ValidationFinding> findings, Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    private static double average(List<RecordQualityScore> records) {
        double mean = records.stream().mapToInt(RecordQualityScore::score).average()
                .orElse(MAX_SCORE);
        return Math.round(mean * 100.0) / 100.0;
    }

    private static int minimum(List<RecordQualityScore> records) {
        return records.stream().mapToInt(RecordQualityScore::score).min().orElse(MAX_SCORE);
    }

    private static String key(String table, String recordId) {
        return table + "|" + recordId;
    }
}
