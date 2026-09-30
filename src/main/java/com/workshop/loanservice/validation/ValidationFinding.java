package com.workshop.loanservice.validation;

/**
 * A single data-quality issue detected on one legacy record.
 *
 * @param severity ERROR or WARNING
 * @param table    legacy table name, e.g. {@code CDW_BORR_MSTR}
 * @param recordId primary key of the offending row
 * @param column   legacy column the finding is attached to
 * @param message  human-readable description including the offending value
 */
public record ValidationFinding(
        Severity severity,
        String table,
        String recordId,
        String column,
        String message) {
}
