package com.workshop.loanservice.service;

/**
 * Thrown by {@link LoanService} when a requested loan or borrower does not exist; mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}
