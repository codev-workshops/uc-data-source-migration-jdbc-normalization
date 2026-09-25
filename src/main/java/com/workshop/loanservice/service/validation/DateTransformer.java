package com.workshop.loanservice.service.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Parses legacy {@code MM/DD/YYYY} date strings with real calendar validation (e.g. rejects {@code
 * 02/30/2020} and {@code 2020-02-15}).
 */
public final class DateTransformer implements FieldTransformer<LocalDate> {

  /** Rule id for null or blank dates. */
  public static final String RULE_MISSING = "DATE_MISSING";

  /** Rule id for dates not in {@code MM/DD/YYYY} form. */
  public static final String RULE_FORMAT = "DATE_FORMAT";

  /** Rule id for well-formed dates that do not exist on the calendar. */
  public static final String RULE_CALENDAR = "DATE_CALENDAR";

  private static final Pattern SHAPE = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
  private static final DateTimeFormatter STRICT_US =
      DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

  @Override
  public TransformResult<LocalDate> transform(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return TransformResult.invalid(
          ValidationViolation.error(fieldName, raw, RULE_MISSING, "Date is null or blank"));
    }
    String trimmed = raw.trim();
    if (!SHAPE.matcher(trimmed).matches()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_FORMAT, "Date must be in MM/DD/YYYY format"));
    }
    try {
      return TransformResult.ok(LocalDate.parse(trimmed, STRICT_US));
    } catch (DateTimeParseException e) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_CALENDAR, "Date is not a real calendar date"));
    }
  }
}
