package com.workshop.loanservice.migration;

public class LegacyDataMigrationException extends RuntimeException {

    public LegacyDataMigrationException(String message) {
        super(message);
    }

    public LegacyDataMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
