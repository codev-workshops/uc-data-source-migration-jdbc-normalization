package com.workshop.loanservice.exception;

/** Raised when a requested resource does not exist; mapped to HTTP 404 by the advice. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String resourceType, String id) {
    super(resourceType + " not found: " + id);
  }
}
