package com.workshop.loanservice.dto;

import com.workshop.loanservice.service.validation.ValidationViolation;

/**
 * API-facing view of a {@link ValidationViolation} attached to a translated record.
 *
 * @param field legacy column that failed
 * @param rawValue raw legacy value
 * @param ruleId stable rule identifier
 * @param severity {@code ERROR} or {@code WARN}
 * @param message human-readable explanation
 */
public record FieldViolationDto(
    String field, String rawValue, String ruleId, String severity, String message) {

  /** Converts a domain violation into its API representation. */
  public static FieldViolationDto from(ValidationViolation v) {
    return new FieldViolationDto(
        v.field(), v.rawValue(), v.ruleId(), v.severity().name(), v.message());
  }
}
