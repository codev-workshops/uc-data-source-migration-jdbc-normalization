package com.workshop.loanservice.service.validation;

/**
 * A single data-quality rule failure observed while transforming or cross-checking legacy data.
 *
 * @param entityType legacy table the record belongs to (e.g. {@code CDW_LN_ACCT})
 * @param recordKey primary key of the offending record, or {@code null} when not yet bound
 * @param field legacy column (or synthetic cross-field name) that failed
 * @param rawValue raw legacy value as read from the database, may be {@code null}
 * @param ruleId stable machine-readable rule identifier
 * @param severity {@link Severity#ERROR} blocks migration, {@link Severity#WARN} needs review
 * @param message human-readable explanation
 */
public record ValidationViolation(
    String entityType,
    String recordKey,
    String field,
    String rawValue,
    String ruleId,
    Severity severity,
    String message) {

  /** Severity of a violation. */
  public enum Severity {
    ERROR,
    WARN
  }

  /** Returns a copy of this violation bound to the given entity type and record key. */
  public ValidationViolation bind(String entityType, String recordKey) {
    return new ValidationViolation(
        entityType, recordKey, field, rawValue, ruleId, severity, message);
  }

  /** Creates an unbound {@link Severity#ERROR} violation. */
  public static ValidationViolation error(
      String field, String rawValue, String ruleId, String message) {
    return new ValidationViolation(null, null, field, rawValue, ruleId, Severity.ERROR, message);
  }

  /** Creates an unbound {@link Severity#WARN} violation. */
  public static ValidationViolation warn(
      String field, String rawValue, String ruleId, String message) {
    return new ValidationViolation(null, null, field, rawValue, ruleId, Severity.WARN, message);
  }
}
