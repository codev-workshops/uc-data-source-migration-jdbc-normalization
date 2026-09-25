package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.InvalidInputRecord;
import com.workshop.loanservice.repository.InvalidInputRepository;
import com.workshop.loanservice.service.validation.DataQualityResult;
import com.workshop.loanservice.service.validation.DataQualityValidator;
import com.workshop.loanservice.service.validation.ValidationModeResolver;
import com.workshop.loanservice.service.validation.ValidationViolation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * API validation mode: validates request inputs and the legacy records an endpoint touches, and
 * quarantines every violation in {@code DQ_INVALID_INPUT}.
 *
 * <p>All methods are no-ops when {@link ValidationModeResolver#isEnabled()} is false, so callers
 * can invoke them unconditionally.
 */
@Service
public class ApiValidationService {

  private final ValidationModeResolver modeResolver;
  private final DataQualityValidator validator;
  private final InvalidInputRepository repository;

  public ApiValidationService(
      ValidationModeResolver modeResolver,
      DataQualityValidator validator,
      InvalidInputRepository repository) {
    this.modeResolver = modeResolver;
    this.validator = validator;
    this.repository = repository;
  }

  /** True when the current request runs in validation mode. */
  public boolean isEnabled() {
    return modeResolver.isEnabled();
  }

  /**
   * Persists request-input violations. Returns true when at least one ERROR was recorded so the
   * caller can decide whether to continue.
   */
  public boolean recordInput(String inputName, String inputValue, List<ValidationViolation> found) {
    if (!isEnabled() || found.isEmpty()) {
      return false;
    }
    persist(inputName, inputValue, found);
    return found.stream().anyMatch(v -> v.severity() == ValidationViolation.Severity.ERROR);
  }

  /** Persists a single violation (e.g. INPUT_NOT_FOUND). */
  public void record(String inputName, String inputValue, ValidationViolation violation) {
    recordInput(inputName, inputValue, List.of(violation));
  }

  /**
   * Runs the full {@link DataQualityValidator} (field, cross-field and referential rules) and
   * quarantines every violation whose record is in {@code recordKeys} for {@code entityType}.
   * Reference rules need every parent table, so the whole data set is validated and then filtered.
   */
  public void recordRecords(
      String inputName, String inputValue, String entityType, Collection<String> recordKeys) {
    if (!isEnabled() || recordKeys.isEmpty()) {
      return;
    }
    Set<String> keys = Set.copyOf(recordKeys);
    DataQualityResult result = validator.validateAll();
    List<ValidationViolation> matching =
        result.violations().stream()
            .filter(v -> entityType.equals(v.entityType()) && keys.contains(v.recordKey()))
            .toList();
    persist(inputName, inputValue, matching);
  }

  /** Quarantines every violation of a full validator run (used by the report endpoint). */
  public void recordAll(DataQualityResult result) {
    if (!isEnabled()) {
      return;
    }
    persist(null, null, result.violations());
  }

  private void persist(String inputName, String inputValue, List<ValidationViolation> violations) {
    if (violations.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    String endpoint = modeResolver.currentEndpoint();
    List<InvalidInputRecord> rows = new ArrayList<>(violations.size());
    for (ValidationViolation v : violations) {
      InvalidInputRecord row = new InvalidInputRecord();
      row.setRecordedAt(now);
      row.setEndpoint(endpoint);
      row.setInputName(inputName);
      row.setInputValue(inputValue);
      row.setEntityType(v.entityType());
      row.setRecordKey(v.recordKey());
      row.setField(v.field());
      row.setRawValue(v.rawValue());
      row.setRuleId(v.ruleId());
      row.setSeverity(v.severity().name());
      row.setMessage(v.message());
      rows.add(row);
    }
    repository.saveAll(rows);
  }
}
