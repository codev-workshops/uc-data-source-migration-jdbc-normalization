package com.workshop.loanservice.service.validation;

/** Thrown when a well-formed key resolves to no legacy record; mapped to HTTP 404. */
public class RecordNotFoundException extends RuntimeException {

  public RecordNotFoundException(String table, String key) {
    super(table + " record not found: " + key);
  }
}
