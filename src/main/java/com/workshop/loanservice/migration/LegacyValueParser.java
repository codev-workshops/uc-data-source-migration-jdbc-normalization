package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts legacy CDW string values into typed values.
 *
 * <p>Every value is sanitized first ({@link #clean}): whitespace-like characters become spaces,
 * other control/format characters are removed and the result is trimmed. Blank values become
 * {@code null}; values that cannot be parsed raise {@link MalformedLegacyDataException}.
 */
public final class LegacyValueParser {

    /** Column precision/scale, e.g. {@code DECIMAL(12, 2)}. */
    public record DecimalSpec(int precision, int scale) {
    }

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE_SHAPE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");

    private static final String NUMBER = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?|\\.\\d+";
    /** {@code 1,487.02}, {@code $285,000}, {@code -12.50}, {@code -$12.50}, {@code $-12.50}. */
    private static final Pattern SIGNED_AMOUNT = Pattern.compile("(-)?\\$?(-)?(" + NUMBER + ")");
    /** Accounting negative: {@code (123.45)}, {@code ($1,234.00)}. */
    private static final Pattern ACCOUNTING_AMOUNT = Pattern.compile("\\(\\$?(" + NUMBER + ")\\)");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private static final Pattern WHITESPACE_LIKE = Pattern.compile("[\\t\\n\\x0B\\f\\r\\p{Zs}\\p{Zl}\\p{Zp}]");
    private static final Pattern NON_PRINTABLE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]");

    private LegacyValueParser() {
    }

    /** Amounts such as {@code "1,487.02"}, {@code "$285,000"} or {@code "(12.50)"}, without a column limit. */
    public static BigDecimal parseAmount(String column, String raw) {
        return parseAmount(column, raw, null);
    }

    /** Amounts as above, rejected when they don't fit the target column. */
    public static BigDecimal parseAmount(String column, String raw, DecimalSpec spec) {
        String value = clean(raw);
        if (value == null) return null;

        boolean negative;
        String digits;
        Matcher signed = SIGNED_AMOUNT.matcher(value);
        Matcher accounting = ACCOUNTING_AMOUNT.matcher(value);
        if (signed.matches() && !(signed.group(1) != null && signed.group(2) != null)) {
            negative = signed.group(1) != null || signed.group(2) != null;
            digits = signed.group(3);
        } else if (accounting.matches()) {
            negative = true;
            digits = accounting.group(1);
        } else {
            throw new MalformedLegacyDataException(column, raw, "not a monetary amount");
        }
        BigDecimal amount = new BigDecimal(digits.replace(",", ""));
        return fit(column, raw, negative ? amount.negate() : amount, spec);
    }

    /** Plain decimals such as {@code "4.750"} (no thousands separators, currency or exponent). */
    public static BigDecimal parseDecimal(String column, String raw) {
        return parseDecimal(column, raw, null);
    }

    public static BigDecimal parseDecimal(String column, String raw, DecimalSpec spec) {
        String value = clean(raw);
        if (value == null) return null;
        if (!PLAIN_DECIMAL.matcher(value).matches()) {
            throw new MalformedLegacyDataException(column, raw, "not a decimal number");
        }
        return fit(column, raw, new BigDecimal(value), spec);
    }

    public static Integer parseInteger(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        if (!INTEGER.matcher(value).matches()) {
            throw new MalformedLegacyDataException(column, raw, "not an integer");
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new MalformedLegacyDataException(column, raw, "outside the integer range");
        }
    }

    /** Dates stored as {@code MM/DD/YYYY}; impossible calendar dates (e.g. 02/29/2023) are rejected. */
    public static LocalDate parseDate(String column, String raw) {
        String value = clean(raw);
        if (value == null) return null;
        if (!DATE_SHAPE.matcher(value).matches()) {
            throw new MalformedLegacyDataException(column, raw, "not a valid MM/DD/YYYY date");
        }
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

    /** Sanitized value, or {@code null} when nothing printable is left. */
    public static String clean(String raw) {
        if (raw == null) return null;
        String value = NON_PRINTABLE.matcher(WHITESPACE_LIKE.matcher(raw).replaceAll(" ")).replaceAll("").strip();
        return value.isEmpty() ? null : value;
    }

    /** The raw value with control characters escaped, safe for messages and logs. */
    static String printable(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        raw.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                sb.append(String.format("\\u%04X", cp));
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    private static BigDecimal fit(String column, String raw, BigDecimal value, DecimalSpec spec) {
        if (spec == null) return value;
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > spec.scale()) {
            throw new MalformedLegacyDataException(column, raw,
                    "more than " + spec.scale() + " decimal places for DECIMAL(" + spec.precision() + ", " + spec.scale() + ")");
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > spec.precision() - spec.scale()) {
            throw new MalformedLegacyDataException(column, raw,
                    "too large for DECIMAL(" + spec.precision() + ", " + spec.scale() + ")");
        }
        return value;
    }
}
