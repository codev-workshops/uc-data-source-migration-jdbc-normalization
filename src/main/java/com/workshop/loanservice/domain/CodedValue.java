package com.workshop.loanservice.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A code set entry that knows its legacy CDW short code, its modern stored value and the
 * display label exposed by the REST API.
 */
public interface CodedValue {

  /** Label returned by the API when no value is present (legacy behaviour). */
  String UNKNOWN_LABEL = "Unknown";

  String legacyCode();

  String value();

  String label();

  static <E extends Enum<E> & CodedValue> Optional<E> fromLegacyCode(Class<E> type, String code) {
    return Arrays.stream(type.getEnumConstants())
        .filter(entry -> entry.legacyCode().equals(code))
        .findFirst();
  }

  static <E extends Enum<E> & CodedValue> Optional<E> fromValue(Class<E> type, String value) {
    return Arrays.stream(type.getEnumConstants())
        .filter(entry -> entry.value().equals(value))
        .findFirst();
  }

  /**
   * Returns the API label for a stored modern value. Unrecognised values are returned as-is and
   * a missing value maps to {@link #UNKNOWN_LABEL}, matching the legacy API.
   */
  static <E extends Enum<E> & CodedValue> String labelFor(Class<E> type, String value) {
    if (value == null) {
      return UNKNOWN_LABEL;
    }
    return fromValue(type, value).map(CodedValue::label).orElse(value);
  }
}
