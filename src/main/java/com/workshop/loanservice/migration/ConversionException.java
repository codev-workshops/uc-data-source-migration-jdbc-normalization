package com.workshop.loanservice.migration;

/**
 * Raised by {@link TypeConverter} when a legacy value cannot be strictly
 * converted to its modern type. Carries the offending field and raw value so
 * the migration can produce contextual error entries.
 */
public class ConversionException extends RuntimeException {

    private final String field;
    private final String invalidValue;

    public ConversionException(String field, String invalidValue, String reason) {
        super(reason + " [field=" + field + ", value=" + invalidValue + "]");
        this.field = field;
        this.invalidValue = invalidValue;
    }

    public String getField() {
        return field;
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}
