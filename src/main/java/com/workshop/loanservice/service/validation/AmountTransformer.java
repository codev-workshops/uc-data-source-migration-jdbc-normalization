package com.workshop.loanservice.service.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Parses legacy monetary strings such as {@code "285,000"} or {@code "1,487.02"}.
 *
 * <p>Comma-grouped input must be grouped in exact thousands; anything else (misplaced commas, more
 * than two decimals, negative sign, currency symbol, blank) is rejected. This is the highest-risk
 * transformation in the migration because the legacy code used to coerce failures to zero.
 */
public final class AmountTransformer implements FieldTransformer<BigDecimal> {

  /** Rule id for null or blank amounts. */
  public static final String RULE_MISSING = "AMT_MISSING";

  /** Rule id for amounts that do not match the accepted legacy format. */
  public static final String RULE_FORMAT = "AMT_FORMAT";

  private static final Pattern LEGACY_AMOUNT =
      Pattern.compile("^\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?$|^\\d+(\\.\\d{1,2})?$");

  @Override
  public TransformResult<BigDecimal> transform(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName, raw, RULE_MISSING, "Amount is null or blank; refusing to default to 0"));
    }
    String trimmed = raw.trim();
    if (!LEGACY_AMOUNT.matcher(trimmed).matches()) {
      return TransformResult.invalid(
          ValidationViolation.error(
              fieldName,
              raw,
              RULE_FORMAT,
              "Amount does not match ^\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?$ or ^\\d+(\\.\\d{1,2})?$"));
    }
    return TransformResult.ok(new BigDecimal(trimmed.replace(",", "")));
  }
}
