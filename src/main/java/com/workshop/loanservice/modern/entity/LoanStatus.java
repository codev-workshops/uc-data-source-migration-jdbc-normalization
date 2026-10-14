package com.workshop.loanservice.modern.entity;

/**
 * Loan account status stored in {@code loan_accounts.status}. The label is the text the API
 * returns.
 */
public enum LoanStatus {
    ACTIVE("Active"),
    CLOSED("Closed"),
    DEFAULT("Default"),
    FORBEARANCE("Forbearance");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
