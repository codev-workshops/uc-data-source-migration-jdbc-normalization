package com.workshop.loanservice.service.validation;

import java.util.List;

/** Thrown in validation mode when a request input fails {@link ApiInputValidator} rules. */
public class InvalidInputException extends RuntimeException {

  private final transient List<ValidationViolation> violations;

  public InvalidInputException(List<ValidationViolation> violations) {
    super(violations.isEmpty() ? "Invalid input" : violations.get(0).message());
    this.violations = List.copyOf(violations);
  }

  public List<ValidationViolation> getViolations() {
    return violations;
  }
}
