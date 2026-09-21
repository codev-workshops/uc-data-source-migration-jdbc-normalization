package com.workshop.loanservice.service.migration;

/**
 * Raised when a legacy value cannot be transformed into its modern representation.
 * Always carries the legacy record id and field so the failure is attributable.
 */
public class MigrationException extends RuntimeException {

    private final String recordId;
    private final String field;

    public MigrationException(String recordId, String field, String reason) {
        super("record " + recordId + ", field " + field + ": " + reason);
        this.recordId = recordId;
        this.field = field;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getField() {
        return field;
    }
}
