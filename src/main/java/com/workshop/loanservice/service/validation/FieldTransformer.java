package com.workshop.loanservice.service.validation;

/**
 * Converts a raw legacy string column into a strongly typed value while recording every rule the
 * input violates.
 *
 * <p>Implementations must never silently default: an unparseable, blank or out-of-range input
 * yields a {@code null} value and one or more {@link ValidationViolation}s.
 *
 * @param <T> target type produced by this transformer
 */
public interface FieldTransformer<T> {

  /**
   * Transforms {@code raw} into the target type.
   *
   * @param raw raw legacy value, may be {@code null}
   * @param fieldName legacy column name used in violation reports
   * @return parsed value and any violations; never {@code null}
   */
  TransformResult<T> transform(String raw, String fieldName);
}
