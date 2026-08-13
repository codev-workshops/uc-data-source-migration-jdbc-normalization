package com.workshop.loanservice.migration;

/** Raised when a legacy value cannot be converted to its modern type. */
public class MigrationDataException extends RuntimeException {

    public MigrationDataException(String message) {
        super(message);
    }

    public MigrationDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
