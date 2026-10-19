package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Converts legacy CDW VARCHAR values into typed values. Blank values become {@code null};
 * malformed values raise a {@link LegacyDataException} naming the offending column. Messages never
 * include the raw value, because they are logged and the columns may hold PII.
 */
final class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private LegacyValueParser() {
    }

    static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String require(String column, String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new LegacyDataException(column + " is required but was blank");
        }
        return value;
    }

    static <T> T require(String column, T value) {
        if (value == null) {
            throw new LegacyDataException(column + " is required but was blank");
        }
        return value;
    }

    /** Parses amounts such as {@code "1,487.02"} or {@code "285,000"}; thousands separators are stripped. */
    static BigDecimal parseDecimal(String column, String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new LegacyDataException(column + " is not a valid decimal", e);
        }
    }

    static Integer parseInteger(String column, String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new LegacyDataException(column + " is not a valid integer", e);
        }
    }

    /** Parses legacy {@code MM/DD/YYYY} dates, rejecting impossible dates such as 02/30/2020. */
    static LocalDate parseDate(String column, String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new LegacyDataException(column + " is not a valid MM/DD/YYYY date", e);
        }
    }

    static LocalDateTime parseTimestamp(String column, String raw) {
        LocalDate date = parseDate(column, raw);
        return date == null ? null : date.atStartOfDay();
    }
}
