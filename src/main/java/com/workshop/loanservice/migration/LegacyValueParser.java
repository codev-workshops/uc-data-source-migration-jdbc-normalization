package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Converts the legacy CDW string representations into real Java types.
 *
 * <p>Every method is strict: a value that is present but unparseable throws
 * {@link LegacyValueParseException} rather than silently becoming zero or null. Silent coercion is
 * how a migration loses money, so bad rows are rejected and reported instead.
 */
@Component
public class LegacyValueParser {

    /** Legacy dates are {@code MM/DD/YYYY}. STRICT rejects impossible dates such as 02/30/2020. */
    private static final DateTimeFormatter LEGACY_DATE =
        DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public BigDecimal parseAmount(String raw, String field) {
        if (isBlank(raw)) {
            return null;
        }
        String normalized = raw.replace(",", "").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new LegacyValueParseException(field, raw, "not a decimal amount");
        }
    }

    public BigDecimal parseDecimal(String raw, String field) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new LegacyValueParseException(field, raw, "not a decimal");
        }
    }

    public Integer parseInteger(String raw, String field) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new LegacyValueParseException(field, raw, "not an integer");
        }
    }

    public LocalDate parseDate(String raw, String field) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            throw new LegacyValueParseException(field, raw, "not an MM/DD/YYYY date");
        }
    }

    /** Legacy audit columns are dates, not timestamps; midnight is the only defensible time. */
    public LocalDateTime parseTimestamp(String raw, String field) {
        LocalDate date = parseDate(raw, field);
        return date == null ? null : date.atStartOfDay();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
