package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The transformations described in {@code data/mappings/column_mappings.md}: {@code MM/DD/YYYY}
 * strings to dates and comma-separated strings to full precision decimals. Unparseable values are a
 * {@link MalformedRecordException}.
 */
final class LegacyValues {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private LegacyValues() {
    }

    static String requiredText(String value, String field) {
        String text = optionalText(value);
        if (text == null) {
            throw new MalformedRecordException(field + " is null or blank");
        }
        return text;
    }

    static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static LocalDate requiredDate(String value, String field) {
        LocalDate date = optionalDate(value, field);
        if (date == null) {
            throw new MalformedRecordException(field + " is null or blank");
        }
        return date;
    }

    static LocalDate optionalDate(String value, String field) {
        String text = optionalText(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new MalformedRecordException(field + " is not a MM/DD/YYYY date: '" + text + "'");
        }
    }

    static LocalDateTime optionalTimestamp(String value, String field) {
        LocalDate date = optionalDate(value, field);
        return date == null ? null : date.atStartOfDay();
    }

    static BigDecimal requiredAmount(String value, String field) {
        BigDecimal amount = optionalAmount(value, field);
        if (amount == null) {
            throw new MalformedRecordException(field + " is null or blank");
        }
        return amount;
    }

    /** Comma-stripped, full precision: no rounding, truncation or rescaling. */
    static BigDecimal optionalAmount(String value, String field) {
        String text = optionalText(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new MalformedRecordException(field + " is not a decimal: '" + text + "'");
        }
    }

    static Integer requiredInteger(String value, String field) {
        Integer parsed = optionalInteger(value, field);
        if (parsed == null) {
            throw new MalformedRecordException(field + " is null or blank");
        }
        return parsed;
    }

    static Integer optionalInteger(String value, String field) {
        String text = optionalText(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new MalformedRecordException(field + " is not an integer: '" + text + "'");
        }
    }
}
