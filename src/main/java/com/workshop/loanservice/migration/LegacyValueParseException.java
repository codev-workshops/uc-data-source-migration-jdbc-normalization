package com.workshop.loanservice.migration;

/** Thrown when a legacy value is present but cannot be converted to its modern type. */
public class LegacyValueParseException extends RuntimeException {

    private final String field;

    public LegacyValueParseException(String field, String rawValue, String reason) {
        super(field + "='" + rawValue + "' " + reason);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
