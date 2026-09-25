package com.workshop.loanservice.service.validation;

import com.workshop.loanservice.service.validation.ValidationViolation.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates raw request inputs (path variables, query parameters) before they are used to query the
 * legacy tables. Violations are bound to entity type {@value #ENTITY_TYPE}.
 */
public final class ApiInputValidator {

  /** Entity type used for request-input violations. */
  public static final String ENTITY_TYPE = "API_INPUT";

  /** Input is null or blank. */
  public static final String RULE_MISSING = "INPUT_MISSING";

  /** Input does not match the legacy key format. */
  public static final String RULE_FORMAT = "INPUT_FORMAT";

  /** Input has the right shape but resolves to no legacy record. */
  public static final String RULE_NOT_FOUND = "INPUT_NOT_FOUND";

  /** Input contains characters that are never valid in a legacy key. */
  public static final String RULE_UNSAFE = "INPUT_UNSAFE";

  /** Legacy loan account numbers look like {@code LN-2018-00089}. */
  public static final Pattern LOAN_ACCOUNT_NUMBER = Pattern.compile("^LN-\\d{4}-\\d{5}$");

  /** Legacy borrower ids look like {@code B-10001}. */
  public static final Pattern BORROWER_ID = Pattern.compile("^B-\\d{5}$");

  private static final Pattern SAFE_CHARS = Pattern.compile("^[A-Za-z0-9_-]*$");
  private static final int MAX_LENGTH = 20;

  private ApiInputValidator() {}

  /** Validates a loan account number path variable. */
  public static List<ValidationViolation> loanAccountNumber(String inputName, String raw) {
    return key(inputName, raw, LOAN_ACCOUNT_NUMBER, "LN-YYYY-NNNNN");
  }

  /** Validates a borrower id path variable. */
  public static List<ValidationViolation> borrowerId(String inputName, String raw) {
    return key(inputName, raw, BORROWER_ID, "B-NNNNN");
  }

  /** Records that a well-formed key resolved to no legacy row. */
  public static ValidationViolation notFound(String inputName, String raw, String table) {
    return new ValidationViolation(
        ENTITY_TYPE,
        raw,
        inputName,
        raw,
        RULE_NOT_FOUND,
        Severity.ERROR,
        "No " + table + " record for key " + raw);
  }

  private static List<ValidationViolation> key(
      String inputName, String raw, Pattern pattern, String example) {
    List<ValidationViolation> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      out.add(violation(inputName, raw, RULE_MISSING, Severity.ERROR, "Input is required"));
      return out;
    }
    if (raw.length() > MAX_LENGTH || !SAFE_CHARS.matcher(raw).matches()) {
      out.add(
          violation(
              inputName,
              raw,
              RULE_UNSAFE,
              Severity.ERROR,
              "Input exceeds " + MAX_LENGTH + " chars or contains disallowed characters"));
      return out;
    }
    if (!pattern.matcher(raw).matches()) {
      out.add(
          violation(inputName, raw, RULE_FORMAT, Severity.ERROR, "Input must match " + example));
    }
    return out;
  }

  private static ValidationViolation violation(
      String inputName, String raw, String ruleId, Severity severity, String message) {
    return new ValidationViolation(ENTITY_TYPE, raw, inputName, raw, ruleId, severity, message);
  }
}
