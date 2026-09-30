package com.workshop.loanservice.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Strict parsers for the string-typed values stored in the legacy CDW tables.
 * Every parser returns {@link Optional#empty()} for null or malformed input instead of throwing.
 */
public final class LegacyValues {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("-?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?");

    private LegacyValues() {
    }

    /** Parses an {@code MM/DD/YYYY} date, rejecting impossible dates such as 02/30/2020. */
    public static Optional<LocalDate> parseDate(String value) {
        if (value == null || !DATE_PATTERN.matcher(value).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Parses an amount with optional thousands grouping such as {@code "1,487.02"}. */
    public static Optional<BigDecimal> parseAmount(String value) {
        if (value == null || !AMOUNT_PATTERN.matcher(value.trim()).matches()) {
            return Optional.empty();
        }
        return parseDecimal(value.replace(",", ""));
    }

    /** Parses a plain decimal such as {@code "4.750"}; commas are not accepted. */
    public static Optional<BigDecimal> parseDecimal(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return DECIMAL_PATTERN.matcher(trimmed).matches()
                ? Optional.of(new BigDecimal(trimmed))
                : Optional.empty();
    }

    /** Parses a plain integer such as {@code "360"}. */
    public static Optional<Integer> parseInteger(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (!INTEGER_PATTERN.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
