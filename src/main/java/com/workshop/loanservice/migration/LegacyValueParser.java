package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.LegacyCoded;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Parses CDW string columns into typed values. Blank input yields {@code null}; malformed input
 * throws {@link IllegalArgumentException} with a reason that ends up in the migration report.
 */
final class LegacyValueParser {

    private static final DateTimeFormatter LEGACY_DATE =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern AMOUNT = Pattern.compile("-?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?");
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private LegacyValueParser() {}

    static LocalDate date(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date '" + value + "'", e);
        }
    }

    static LocalDateTime timestamp(String value) {
        LocalDate date = date(value);
        return date == null ? null : date.atStartOfDay();
    }

    /** Accepts {@code 1,487.02} or {@code 1487.02}; rejects bad grouping like {@code 1,48}. */
    static BigDecimal amount(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!AMOUNT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("invalid amount '" + value + "'");
        }
        return new BigDecimal(trimmed.replace(",", ""));
    }

    static Integer integer(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!INTEGER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("invalid integer '" + value + "'");
        }
        return Integer.valueOf(trimmed);
    }

    static <E extends Enum<E> & LegacyCoded> E code(Class<E> type, String legacyCode) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getLegacyCode().equals(legacyCode)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "unknown " + type.getSimpleName() + " code '" + legacyCode + "'");
    }

    static <T> T required(T value, String column) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
