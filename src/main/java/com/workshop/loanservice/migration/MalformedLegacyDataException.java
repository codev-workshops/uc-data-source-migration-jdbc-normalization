package com.workshop.loanservice.migration;

/**
 * A legacy column value that cannot be converted to its modern type.
 */
public class MalformedLegacyDataException extends RuntimeException {

    public MalformedLegacyDataException(String column, String value, String reason) {
        super(column + "='" + value + "': " + reason);
    }
}
