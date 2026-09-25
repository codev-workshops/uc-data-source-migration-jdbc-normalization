package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.DataQualityReportDto;
import com.workshop.loanservice.dto.InvalidInputDto;
import com.workshop.loanservice.entity.InvalidInputRecord;
import com.workshop.loanservice.repository.InvalidInputRepository;
import com.workshop.loanservice.service.ApiValidationService;
import com.workshop.loanservice.service.DataQualityReportService;
import com.workshop.loanservice.service.validation.DataQualityResult;
import com.workshop.loanservice.service.validation.DataQualityValidator;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the legacy data-quality report as JSON and Markdown, and the {@code DQ_INVALID_INPUT}
 * quarantine populated by API validation mode.
 */
@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

  private final DataQualityReportService reportService;
  private final DataQualityValidator validator;
  private final ApiValidationService apiValidation;
  private final InvalidInputRepository invalidInputs;

  public DataQualityController(
      DataQualityReportService reportService,
      DataQualityValidator validator,
      ApiValidationService apiValidation,
      InvalidInputRepository invalidInputs) {
    this.reportService = reportService;
    this.validator = validator;
    this.apiValidation = apiValidation;
    this.invalidInputs = invalidInputs;
  }

  @GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
  public DataQualityReportDto getReport() {
    return reportService.buildReport(validateAll());
  }

  @GetMapping(value = "/report/markdown", produces = MediaType.TEXT_MARKDOWN_VALUE)
  public String getReportMarkdown() {
    return reportService.renderMarkdown(reportService.buildReport(validateAll()));
  }

  /** Lists quarantined violations, optionally filtered by rule or by entity type + record key. */
  @GetMapping(value = "/invalid-inputs", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<InvalidInputDto> getInvalidInputs(
      @RequestParam(required = false) String ruleId,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String recordKey) {
    List<InvalidInputRecord> rows;
    if (ruleId != null) {
      rows = invalidInputs.findByRuleIdOrderByIdDesc(ruleId);
    } else if (entityType != null && recordKey != null) {
      rows = invalidInputs.findByEntityTypeAndRecordKeyOrderByIdDesc(entityType, recordKey);
    } else {
      rows = invalidInputs.findAllByOrderByRecordedAtDescIdDesc();
    }
    return rows.stream().map(InvalidInputDto::from).toList();
  }

  /** Clears the quarantine table; returns the number of rows removed. */
  @DeleteMapping(value = "/invalid-inputs", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Long> clearInvalidInputs() {
    long count = invalidInputs.count();
    invalidInputs.deleteAllInBatch();
    return Map.of("deleted", count);
  }

  private DataQualityResult validateAll() {
    DataQualityResult result = validator.validateAll();
    apiValidation.recordAll(result);
    return result;
  }
}
