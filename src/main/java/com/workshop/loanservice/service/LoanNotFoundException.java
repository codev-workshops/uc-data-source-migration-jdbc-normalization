package com.workshop.loanservice.service;

/**
 * Signals a missing resource without echoing what the caller sent. The type of the thing that was
 * not found is safe to expose; the identifier is not, because reflecting request input into a
 * response body is how log-injection and XSS payloads travel.
 */
public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(String resourceType) {
        super(resourceType + " not found");
    }
}
