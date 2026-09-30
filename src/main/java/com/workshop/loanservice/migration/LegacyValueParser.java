package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.QuarantineReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Strict parsers for legacy CDW string values, one per transformation rule in {@code
 * docs/proposed-column-mappings.md}. Blank input yields {@code null}; unparseable input throws
 * {@link RowRejectedException} so the row is quarantined rather than coerced.
 */
public final class LegacyValueParser {

  private static final DateTimeFormatter LEGACY_DATE =
      DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final LocalDate LAST_REAL_EXPIRY = LocalDate.of(2098, 12, 31);
  private static final Map<String, String> CODE_RENAMES = Map.of("SELF-EMP", "SELF_EMPLOYED");
  private static final int AMOUNT_SCALE = 2;

  private LegacyValueParser() {}

  /** T-COPY: trimmed value, or {@code null} if blank. */
  public static String text(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** T-COPY for NOT NULL targets. */
  public static String requireText(String field, String raw) {
    String value = text(raw);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  /** T-DATE: strict {@code MM/dd/yyyy}. */
  public static LocalDate parseDate(String field, String raw) {
    String value = text(raw);
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value, LEGACY_DATE);
    } catch (DateTimeParseException e) {
      throw malformed(field, raw, "MM/dd/yyyy date");
    }
  }

  /** T-DATE for NOT NULL targets. */
  public static LocalDate requireDate(String field, String raw) {
    LocalDate value = parseDate(field, raw);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  /** T-TS: T-DATE widened to midnight. */
  public static LocalDateTime requireTimestamp(String field, String raw) {
    return requireDate(field, raw).atStartOfDay();
  }

  /** T-AMT: strips {@code ,} and a leading {@code $}, scale 2 HALF_UP. Blank is null, not zero. */
  public static BigDecimal parseAmount(String field, String raw) {
    String value = text(raw);
    if (value == null) {
      return null;
    }
    String digits = value.replace(",", "");
    if (digits.startsWith("$")) {
      digits = digits.substring(1);
    }
    return toDecimal(field, raw, digits, AMOUNT_SCALE);
  }

  /** T-AMT for NOT NULL targets. */
  public static BigDecimal requireAmount(String field, String raw) {
    BigDecimal value = parseAmount(field, raw);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  /** T-DEC: plain decimal scaled to the target column. */
  public static BigDecimal parseDecimal(String field, String raw, int scale) {
    String value = text(raw);
    return value == null ? null : toDecimal(field, raw, value, scale);
  }

  /** T-DEC for NOT NULL targets. */
  public static BigDecimal requireDecimal(String field, String raw, int scale) {
    BigDecimal value = parseDecimal(field, raw, scale);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  /** T-INT: integer with optional thousands separators. */
  public static Integer parseInteger(String field, String raw) {
    String value = text(raw);
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value.replace(",", ""));
    } catch (NumberFormatException e) {
      throw malformed(field, raw, "integer");
    }
  }

  /** T-INT narrowed to a {@code SMALLINT} column. */
  public static Short parseShort(String field, String raw) {
    Integer value = parseInteger(field, raw);
    if (value == null) {
      return null;
    }
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
      throw malformed(field, raw, "SMALLINT");
    }
    return value.shortValue();
  }

  /** T-CODE / T-CODE-MAP: trimmed, upper-cased and renamed; existence is checked by the caller. */
  public static String normaliseCode(String field, String raw) {
    String code = requireText(field, raw).toUpperCase(Locale.ROOT);
    return CODE_RENAMES.getOrDefault(code, code);
  }

  /** T-BOOL: {@code ACT} is true, anything else false. */
  public static boolean activeFlag(String raw) {
    return "ACT".equalsIgnoreCase(text(raw));
  }

  /** T-SENTINEL: expiry dates after 12/31/2098 mean "open-ended". */
  public static LocalDate expirySentinelToNull(LocalDate expiry) {
    return expiry != null && expiry.isAfter(LAST_REAL_EXPIRY) ? null : expiry;
  }

  private static BigDecimal toDecimal(String field, String raw, String digits, int scale) {
    try {
      return new BigDecimal(digits).setScale(scale, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      throw malformed(field, raw, "decimal");
    }
  }

  private static RowRejectedException missing(String field) {
    return new RowRejectedException(QuarantineReason.MISSING_REQUIRED, field, "value is required");
  }

  private static RowRejectedException malformed(String field, String raw, String expected) {
    return new RowRejectedException(
        QuarantineReason.MALFORMED_VALUE, field, "'" + raw + "' is not a valid " + expected);
  }
}
