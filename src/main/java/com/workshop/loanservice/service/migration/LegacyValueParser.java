package com.workshop.loanservice.service.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Parses loosely typed legacy CDW string values into modern typed values.
 *
 * <p>Every parser has two flavours: {@code require*} rejects null/blank input with a
 * {@link MigrationException}, {@code optional*} maps null/blank to {@code null}. Malformed
 * non-blank input always raises {@link MigrationException}; nothing is ever defaulted.
 */
public final class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private LegacyValueParser() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String optionalText(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static String requireText(String value, String recordId, String field) {
        if (isBlank(value)) {
            throw new MigrationException(recordId, field, "value is null or blank");
        }
        return value.trim();
    }

    public static LocalDate requireDate(String value, String recordId, String field) {
        String text = requireText(value, recordId, field);
        try {
            return LocalDate.parse(text, LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new MigrationException(recordId, field, "not a MM/DD/YYYY date: '" + text + "'");
        }
    }

    public static LocalDate optionalDate(String value, String recordId, String field) {
        return isBlank(value) ? null : requireDate(value, recordId, field);
    }

    public static LocalDateTime requireTimestamp(String value, String recordId, String field) {
        return requireDate(value, recordId, field).atStartOfDay();
    }

    public static LocalDateTime optionalTimestamp(String value, String recordId, String field) {
        return isBlank(value) ? null : requireTimestamp(value, recordId, field);
    }

    /** Parses an amount such as {@code "1,487.02"}; thousands separators are removed. */
    public static BigDecimal requireAmount(String value, String recordId, String field) {
        String text = requireText(value, recordId, field).replace(",", "");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new MigrationException(recordId, field, "not a decimal amount: '" + value.trim() + "'");
        }
    }

    public static BigDecimal optionalAmount(String value, String recordId, String field) {
        return isBlank(value) ? null : requireAmount(value, recordId, field);
    }

    /** Parses an amount and rejects values that do not fit the target {@code DECIMAL(precision, scale)}. */
    public static BigDecimal requireAmount(String value, String recordId, String field, int precision, int scale) {
        return checkPrecision(requireAmount(value, recordId, field), recordId, field, precision, scale);
    }

    public static BigDecimal optionalAmount(String value, String recordId, String field, int precision, int scale) {
        return isBlank(value) ? null : requireAmount(value, recordId, field, precision, scale);
    }

    static BigDecimal checkPrecision(BigDecimal amount, String recordId, String field, int precision, int scale) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > scale) {
            throw new MigrationException(recordId, field,
                    "'" + amount.toPlainString() + "' has more than " + scale + " decimal places");
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > precision - scale) {
            throw new MigrationException(recordId, field,
                    "'" + amount.toPlainString() + "' exceeds DECIMAL(" + precision + "," + scale + ")");
        }
        return amount;
    }

    public static Integer requireInteger(String value, String recordId, String field) {
        String text = requireText(value, recordId, field).replace(",", "");
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new MigrationException(recordId, field, "not an integer: '" + value.trim() + "'");
        }
    }

    public static Integer optionalInteger(String value, String recordId, String field) {
        return isBlank(value) ? null : requireInteger(value, recordId, field);
    }
}
