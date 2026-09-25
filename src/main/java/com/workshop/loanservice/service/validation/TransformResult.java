package com.workshop.loanservice.service.validation;

import java.util.List;

/**
 * Outcome of transforming a single raw legacy field.
 *
 * <p>The value is {@code null} whenever the transformation could not be performed safely; in that
 * case at least one violation is present. A transformer never substitutes a default value.
 *
 * @param <T> parsed target type
 * @param value parsed value, or {@code null} when the raw input was invalid
 * @param violations violations recorded during transformation, never {@code null}
 */
public record TransformResult<T>(T value, List<ValidationViolation> violations) {

  public TransformResult {
    violations = List.copyOf(violations);
  }

  /** Creates a successful result with no violations. */
  public static <T> TransformResult<T> ok(T value) {
    return new TransformResult<>(value, List.of());
  }

  /** Creates a result whose value could not be derived. */
  public static <T> TransformResult<T> invalid(ValidationViolation violation) {
    return new TransformResult<>(null, List.of(violation));
  }

  /** Creates a result that carries a value but also a non-fatal violation. */
  public static <T> TransformResult<T> withWarning(T value, ValidationViolation violation) {
    return new TransformResult<>(value, List.of(violation));
  }

  /** Returns {@code true} when no violations were recorded. */
  public boolean isValid() {
    return violations.isEmpty();
  }

  /** Returns {@code true} when at least one violation is an {@code ERROR}. */
  public boolean hasError() {
    return violations.stream().anyMatch(v -> v.severity() == ValidationViolation.Severity.ERROR);
  }
}
