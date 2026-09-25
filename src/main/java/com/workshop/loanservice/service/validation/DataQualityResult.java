package com.workshop.loanservice.service.validation;

import java.util.List;
import java.util.Map;

/**
 * Raw output of a {@link DataQualityValidator} run.
 *
 * @param recordKeysByTable every record key examined, grouped by legacy table, so that clean
 *     records can be scored as well as dirty ones
 * @param violations all violations found, each bound to a table and record key
 */
public record DataQualityResult(
    Map<String, List<String>> recordKeysByTable, List<ValidationViolation> violations) {}
