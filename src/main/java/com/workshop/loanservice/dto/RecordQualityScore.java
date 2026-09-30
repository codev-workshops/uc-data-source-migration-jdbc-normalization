package com.workshop.loanservice.dto;

/**
 * Quality score (0-100) for one legacy record.
 */
public record RecordQualityScore(
        String table,
        String recordId,
        int score,
        long errorCount,
        long warningCount) {
}
