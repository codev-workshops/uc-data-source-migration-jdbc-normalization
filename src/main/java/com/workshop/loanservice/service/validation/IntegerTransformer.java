package com.workshop.loanservice.service.validation;

import java.util.regex.Pattern;

/**
 * Parses legacy integer strings with an inclusive range check.
 *
 * <p>Factory methods encode the business ranges for credit scores (300-850), loan/product terms
 * (1-480 months) and delinquency days (0-9999).
 */
public final class IntegerTransformer implements FieldTransformer<Integer> {

  /** Rule id for null or blank integers. */
  public static final String RULE_MISSING = "INT_MISSING";

  /** Rule id for values that are not unsigned digits. */
  public static final String RULE_FORMAT = "INT_FORMAT";

  /** Rule id for values outside the configured range. */
  public static final String RULE_RANGE = "INT_RANGE";

  private static final Pattern DIGITS = Pattern.compile("^\\d{1,9}$");

  private final int min;
  private final int max;

  private IntegerTransformer(int min, int max) {
    this.min = min;
    this.max = max;
  }

  /** Transformer for FICO-style credit scores. */
  public static IntegerTransformer creditScore() {
    return new IntegerTransformer(300, 850);
  }

  /** Transformer for loan and product terms in months. */
  public static IntegerTransformer termMonths() {
    return new IntegerTransformer(1, 480);
  }

  /** Transformer for delinquency day counts. */
  public static IntegerTransformer delinquencyDays() {
    return new IntegerTransformer(0, 9999);
  }

  /** Transformer with a custom inclusive range. */
  public static IntegerTransformer range(int min, int max) {
    return new IntegerTransformer(min, max);
  }

  @Override
  public TransformResult<Integer> transform(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return TransformResult.invalid(
          ValidationViolation.error(fieldName, raw, RULE_MISSING, "Integer is null or blank"));
    }
    String trimmed = raw.trim();
    if (!DIGITS.matcher(trimmed).matches()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_FORMAT, "Integer must contain only digits"));
    }
    int value = Integer.parseInt(trimmed);
    if (value < min || value > max) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName,
              raw,
              RULE_RANGE,
              "Integer " + value + " outside allowed range [" + min + ", " + max + "]"));
    }
    return TransformResult.ok(value);
  }
}
