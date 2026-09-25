package com.workshop.loanservice.dto;

import com.workshop.loanservice.entity.InvalidInputRecord;
import java.time.Instant;

/** API view of one {@code DQ_INVALID_INPUT} row. */
public record InvalidInputDto(
    Long id,
    Instant recordedAt,
    String endpoint,
    String inputName,
    String inputValue,
    String entityType,
    String recordKey,
    String field,
    String rawValue,
    String ruleId,
    String severity,
    String message) {

  public static InvalidInputDto from(InvalidInputRecord r) {
    return new InvalidInputDto(
        r.getId(),
        r.getRecordedAt(),
        r.getEndpoint(),
        r.getInputName(),
        r.getInputValue(),
        r.getEntityType(),
        r.getRecordKey(),
        r.getField(),
        r.getRawValue(),
        r.getRuleId(),
        r.getSeverity(),
        r.getMessage());
  }
}
