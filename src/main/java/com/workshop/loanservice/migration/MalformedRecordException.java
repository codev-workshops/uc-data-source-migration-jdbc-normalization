package com.workshop.loanservice.migration;

/**
 * A single legacy record cannot be transformed (null required field, unparseable value or a
 * status/type code that {@code data/mappings/column_mappings.md} does not expand). Caught per
 * record by {@link MigrationService}, which skips that record and keeps migrating.
 */
public class MalformedRecordException extends RuntimeException {

    public MalformedRecordException(String message) {
        super(message);
    }
}
