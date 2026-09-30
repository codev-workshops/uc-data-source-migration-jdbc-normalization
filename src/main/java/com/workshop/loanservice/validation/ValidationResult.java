package com.workshop.loanservice.validation;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a full validation run.
 *
 * @param recordIdsByTable every validated record id, grouped by legacy table in validation order
 * @param findings         all findings across all tables
 */
public record ValidationResult(
        Map<String, List<String>> recordIdsByTable,
        List<ValidationFinding> findings) {
}
