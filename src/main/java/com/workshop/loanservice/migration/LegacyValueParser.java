package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Converts legacy CDW string values into typed values. Blank values become
 * {@code null}; values that cannot be parsed raise {@link MalformedLegacyDataException}.
 */
public final class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private LegacyValueParser() {
    }

    /** Amounts such as {@code "1,487.02"} or {@code "285,000"}. */
    public static BigDecimal parseAmount(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        return toDecimal(column, raw, value.replace(",", ""));
    }

    /** Plain decimals such as {@code "4.750"} (no thousands separators). */
    public static BigDecimal parseDecimal(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        return toDecimal(column, raw, value);
    }

    public static Integer parseInteger(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new MalformedLegacyDataException(column, raw, "not an integer");
        }
    }

    /** Dates stored as {@code MM/DD/YYYY}. */
    public static LocalDate parseDate(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        try {
            return LocalDate.parse(value, LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new MalformedLegacyDataException(column, raw, "not a valid MM/DD/YYYY date");
        }
    }

    public static LocalDateTime parseTimestamp(String column, String raw) {
        LocalDate date = parseDate(column, raw);
        return date == null ? null : date.atStartOfDay();
    }

    static String clean(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    private static BigDecimal toDecimal(String column, String raw, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new MalformedLegacyDataException(column, raw, "not a decimal number");
        }
    }
}
