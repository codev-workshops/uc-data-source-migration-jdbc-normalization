package com.workshop.loanservice.migration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Parses the string-encoded values stored in the legacy CDW tables. Null or blank input yields
 * {@code null}; malformed input raises {@link LegacyDataException} naming the offending column.
 */
public final class LegacyValueParser {

  private static final DateTimeFormatter LEGACY_DATE =
      DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT);

  private LegacyValueParser() {
  }

  /** Parses an {@code MM/DD/YYYY} string. */
  public static LocalDate parseDate(String column, String value) {
    String text = trimToNull(value);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text, LEGACY_DATE);
    } catch (DateTimeParseException e) {
      throw malformed(column, value, e);
    }
  }

  /** Parses an {@code MM/DD/YYYY} string to a timestamp at the start of that day. */
  public static LocalDateTime parseTimestamp(String column, String value) {
    LocalDate date = parseDate(column, value);
    return date == null ? null : date.atStartOfDay();
  }

  /** Parses an amount such as {@code "1,487.02"} by removing thousands separators. */
  public static BigDecimal parseAmount(String column, String value) {
    String text = trimToNull(value);
    return text == null ? null : parseDecimal(column, text.replace(",", ""));
  }

  /** Parses a plain decimal such as {@code "4.750"}. */
  public static BigDecimal parseDecimal(String column, String value) {
    String text = trimToNull(value);
    if (text == null) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw malformed(column, value, e);
    }
  }

  /** Parses a plain integer such as {@code "360"}. */
  public static Integer parseInteger(String column, String value) {
    String text = trimToNull(value);
    if (text == null) {
      return null;
    }
    try {
      return Integer.valueOf(text);
    } catch (NumberFormatException e) {
      throw malformed(column, value, e);
    }
  }

  static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static LegacyDataException malformed(String column, String value, Exception cause) {
    return new LegacyDataException(column + " has malformed value '" + value + "'", cause);
  }
}
