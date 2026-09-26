package com.workshop.loanservice.routing;

import java.util.Locale;

/** The concrete {@link com.workshop.loanservice.service.LoanQueryService} a request is served by. */
public enum ServiceImplementation {
  LEGACY,
  NORMALIZED;

  public static final ServiceImplementation DEFAULT = NORMALIZED;

  /**
   * Parses a request parameter value case-insensitively. Returns {@link #DEFAULT} when the value is
   * null, blank or not a known implementation.
   */
  public static ServiceImplementation fromParam(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return DEFAULT;
    }
  }
}
