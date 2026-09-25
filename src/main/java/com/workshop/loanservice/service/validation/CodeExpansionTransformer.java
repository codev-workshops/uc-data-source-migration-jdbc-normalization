package com.workshop.loanservice.service.validation;

/**
 * Expands a legacy status/type code using an exhaustive {@link LegacyCodeSet}.
 *
 * <p>Unmapped codes are never passed through. They yield the controlled value {@link #UNKNOWN} and
 * an {@code ERROR} violation so the record is surfaced rather than silently carrying a raw code
 * into the modern schema.
 */
public final class CodeExpansionTransformer implements FieldTransformer<String> {

  /** Controlled expansion for null or unmapped codes. */
  public static final String UNKNOWN = "Unknown";

  /** Rule id for null or blank codes. */
  public static final String RULE_MISSING = "CODE_MISSING";

  /** Rule id for codes absent from the dictionary. */
  public static final String RULE_UNMAPPED = "CODE_UNMAPPED";

  private final LegacyCodeSet codeSet;

  public CodeExpansionTransformer(LegacyCodeSet codeSet) {
    this.codeSet = codeSet;
  }

  @Override
  public TransformResult<String> transform(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return TransformResult.withWarning(
          UNKNOWN,
          ValidationViolation.error(
              fieldName, raw, RULE_MISSING, "Code is null or blank; mapped to \"Unknown\""));
    }
    String trimmed = raw.trim();
    return codeSet
        .expand(trimmed)
        .map(TransformResult::ok)
        .orElseGet(
            () ->
                TransformResult.withWarning(
                    UNKNOWN,
                    ValidationViolation.error(
                        fieldName,
                        raw,
                        RULE_UNMAPPED,
                        "Code '"
                            + trimmed
                            + "' is not in the "
                            + codeSet.name()
                            + " dictionary "
                            + codeSet.expansions().keySet()
                            + "; mapped to \"Unknown\"")));
  }
}
