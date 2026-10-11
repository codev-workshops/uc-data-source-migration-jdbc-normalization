package com.workshop.loanservice.migration;

/** Raised when a legacy record cannot be transformed into its modern representation. */
public class LegacyDataException extends RuntimeException {

  public LegacyDataException(String message) {
    super(message);
  }

  public LegacyDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
