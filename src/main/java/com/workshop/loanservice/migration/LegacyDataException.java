package com.workshop.loanservice.migration;

/** Thrown when a legacy value is missing, malformed or cannot be mapped to the modern schema. */
public class LegacyDataException extends RuntimeException {

    public LegacyDataException(String message) {
        super(message);
    }

    public LegacyDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
