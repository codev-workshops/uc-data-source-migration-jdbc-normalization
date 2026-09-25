package com.workshop.loanservice.service.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Parses plain (non-comma-grouped) legacy decimals such as interest rates ({@code "4.750"}) and LTV
 * percentages ({@code "82.5"}).
 */
public final class DecimalTransformer implements FieldTransformer<BigDecimal> {

  /** Rule id for null or blank decimals. */
  public static final String RULE_MISSING = "DEC_MISSING";

  /** Rule id for decimals that are not a plain unsigned number. */
  public static final String RULE_FORMAT = "DEC_FORMAT";

  private static final Pattern PLAIN_DECIMAL = Pattern.compile("^\\d+(\\.\\d+)?$");

  @Override
  public TransformResult<BigDecimal> transform(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_MISSING, "Decimal is null or blank; refusing to default to 0"));
    }
    String trimmed = raw.trim();
    if (!PLAIN_DECIMAL.matcher(trimmed).matches()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_FORMAT, "Decimal must match ^\\d+(\\.\\d+)?$"));
    }
    return TransformResult.ok(new BigDecimal(trimmed));
  }
}
