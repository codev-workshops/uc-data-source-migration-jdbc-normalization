package com.workshop.loanservice.validation;

import java.util.stream.Stream;

/**
 * A check applied to one legacy record, yielding zero or more findings.
 *
 * @param <T> legacy entity type
 */
@FunctionalInterface
public interface ValidationRule<T> {

    Stream<ValidationFinding> validate(T record);
}
