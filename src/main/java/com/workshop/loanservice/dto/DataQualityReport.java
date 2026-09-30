package com.workshop.loanservice.dto;

import com.workshop.loanservice.validation.Severity;
import com.workshop.loanservice.validation.ValidationFinding;

import java.util.List;
import java.util.Map;

/**
 * Data-quality report across all legacy tables.
 */
public record DataQualityReport(
        int totalRecords,
        Map<Severity, Long> findingsBySeverity,
        double averageScore,
        int minimumScore,
        List<TableQualitySummary> tables,
        List<RecordQualityScore> records,
        List<ValidationFinding> findings) {
}
