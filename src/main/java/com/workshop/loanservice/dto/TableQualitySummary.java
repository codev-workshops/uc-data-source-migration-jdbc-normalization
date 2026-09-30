package com.workshop.loanservice.dto;

/**
 * Aggregated quality figures for one legacy table.
 */
public record TableQualitySummary(
        String table,
        int recordCount,
        long errorCount,
        long warningCount,
        double averageScore,
        int minimumScore) {
}
